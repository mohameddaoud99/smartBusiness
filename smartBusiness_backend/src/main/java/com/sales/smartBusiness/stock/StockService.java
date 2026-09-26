package com.sales.smartBusiness.stock;

import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.product.Product;
import com.sales.smartBusiness.product.ProductService;
import com.sales.smartBusiness.security.CurrentUser;
import com.sales.smartBusiness.warehouse.Warehouse;
import com.sales.smartBusiness.warehouse.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * The stock register. Levels are always computed from the movements — nothing here keeps a
 * running counter — and every write is a new row, corrected by an opposite one.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StockService {

    private static final Set<StockMovementType> RESERVATION_TYPES =
            EnumSet.of(StockMovementType.RESERVE, StockMovementType.RELEASE);

    private final StockMovementRepository movementRepository;
    private final ProductService productService;
    private final WarehouseService warehouseService;
    private final CompanyService companyService;
    private final StockMovementMapper movementMapper;
    private final CurrentUser currentUser;

    // ----- Reading -----

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> searchMovements(Long productId, Long warehouseId,
                                                       StockMovementType type, Pageable pageable) {
        return movementRepository.search(currentUser.companyId(), productId, warehouseId, type, pageable)
                .map(movementMapper::toResponse);
    }

    /**
     * One row per good. With a warehouse, the figures are that warehouse's; without, the
     * whole company's. The low-stock flag compares the company-wide available quantity with
     * the product's minimum, so it is only shown (and filterable) without a warehouse.
     */
    @Transactional(readOnly = true)
    public Page<StockLevelResponse> levels(String search, Long warehouseId, boolean lowOnly, Pageable pageable) {
        if (warehouseId != null) {
            warehouseService.findById(warehouseId); // 404 when it is not the caller's
        }
        Page<Product> goods = productService.searchGoods(search, lowOnly && warehouseId == null, pageable);
        Map<Long, Levels> levels = levelsOf(goods.map(Product::getId).getContent(), warehouseId);

        return goods.map(product -> {
            Levels level = levels.getOrDefault(product.getId(), Levels.NONE);
            StockLevelResponse response = new StockLevelResponse();
            response.setProductId(product.getId());
            response.setReference(product.getReference());
            response.setName(product.getName());
            response.setUnit(product.getUnit());
            response.setMinStock(product.getMinStock());
            response.setPhysical(level.physical());
            response.setReserved(level.reserved());
            response.setAvailable(level.available());
            response.setLowStock(warehouseId == null && product.getMinStock() != null
                    && level.available().compareTo(product.getMinStock()) <= 0);
            return response;
        });
    }

    /** The stock figures of the dashboard: what it is worth, and the five first goods at or under their minimum. */
    @Transactional(readOnly = true)
    public StockFigures figures() {
        List<StockMovementType> physical = Arrays.stream(StockMovementType.values()).filter(StockMovementType::isPhysical).toList();
        BigDecimal value = movementRepository.stockValue(currentUser.companyId(), physical);
        Page<StockLevelResponse> low = levels("", null, true, PageRequest.of(0, 5, Sort.by("name")));
        return new StockFigures(value, low.getTotalElements(), low.getContent());
    }

    // ----- Manual movements -----

    /** An entry, an exit or an inventory adjustment. */
    public StockMovementResponse record(StockMovementRequest request) {
        Product product = productService.getStockable(request.getProductId());
        Warehouse warehouse = warehouseService.getAssignable(request.getWarehouseId());
        BigDecimal quantity = request.getQuantity();

        BigDecimal delta;
        switch (request.getType()) {
            case ENTRY -> delta = requirePositive(quantity);
            case EXIT -> {
                requirePositive(quantity);
                ensureAvailable(product, warehouse, quantity);
                delta = quantity.negate();
            }
            case ADJUSTMENT -> {
                // The count is what is really on the shelf: write the difference with the register
                delta = quantity.subtract(levelsOf(List.of(product.getId()), warehouse.getId())
                        .getOrDefault(product.getId(), Levels.NONE).physical());
                if (delta.signum() == 0) {
                    throw new BusinessRuleException("The register already shows this quantity — nothing to adjust");
                }
            }
            default -> throw new BusinessRuleException(
                    "Only an entry, an exit or an adjustment can be recorded by hand");
        }

        return movementMapper.toResponse(write(product, warehouse, request.getType(), delta,
                request.getReason(), null, null));
    }

    /** Moves stock between two warehouses: an exit from the first and an entry into the second. */
    public List<StockMovementResponse> transfer(StockTransferRequest request) {
        if (request.getFromWarehouseId().equals(request.getToWarehouseId())) {
            throw new BusinessRuleException("Choose two different warehouses");
        }
        Product product = productService.getStockable(request.getProductId());
        Warehouse from = warehouseService.getAssignable(request.getFromWarehouseId());
        Warehouse to = warehouseService.getAssignable(request.getToWarehouseId());
        ensureAvailable(product, from, request.getQuantity());

        StockMovement out = write(product, from, StockMovementType.TRANSFER_OUT,
                request.getQuantity().negate(), request.getReason(), null, null);
        StockMovement in = write(product, to, StockMovementType.TRANSFER_IN,
                request.getQuantity(), request.getReason(), null, null);
        return List.of(movementMapper.toResponse(out), movementMapper.toResponse(in));
    }

    // ----- Caused by documents -----

    /**
     * Promises stock to a document (a confirmed sales order) in the default warehouse. A
     * good that forbids negative stock must have enough available — otherwise the whole
     * confirmation is refused, naming the product.
     */
    public void reserve(StockSource source, Long sourceId, List<StockRequirement> requirements) {
        Map<Long, StockRequirement> merged = new LinkedHashMap<>();
        for (StockRequirement requirement : requirements) {
            merged.merge(requirement.product().getId(), requirement,
                    (a, b) -> new StockRequirement(a.product(), a.quantity().add(b.quantity())));
        }
        if (merged.isEmpty()) {
            return;
        }

        Warehouse warehouse = warehouseService.getDefault();
        for (StockRequirement requirement : merged.values()) {
            ensureAvailable(requirement.product(), warehouse, requirement.quantity());
            write(requirement.product(), warehouse, StockMovementType.RESERVE,
                    requirement.quantity(), "Reserved for a sales order", source, sourceId);
        }
    }

    /**
     * Puts the goods of a validated receipt into a warehouse: one ENTRY per product, linked to
     * its document. Nothing to refuse: receiving stock is always possible.
     */
    public void receive(StockSource source, Long sourceId, Warehouse warehouse, List<StockRequirement> goods) {
        Map<Long, StockRequirement> merged = new LinkedHashMap<>();
        for (StockRequirement good : goods) {
            merged.merge(good.product().getId(), good,
                    (a, b) -> new StockRequirement(a.product(), a.quantity().add(b.quantity())));
        }
        for (StockRequirement good : merged.values()) {
            write(good.product(), warehouse, StockMovementType.ENTRY, good.quantity(),
                    "Goods received", source, sourceId);
        }
    }

    /**
     * Takes back what a cancelled receipt had put in: the opposite of its entries. A product that
     * forbids negative stock must still have the goods available, or the cancellation is refused
     * (they have already been sold or reserved).
     */
    public void reverseReceipt(StockSource source, Long sourceId) {
        List<StockMovement> entries = movementRepository.findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(
                currentUser.companyId(), source, sourceId, EnumSet.of(StockMovementType.ENTRY, StockMovementType.EXIT));

        Map<String, BigDecimal> net = new LinkedHashMap<>();
        Map<String, StockMovement> sample = new HashMap<>();
        for (StockMovement movement : entries) {
            String key = movement.getProduct().getId() + ":" + movement.getWarehouse().getId();
            net.merge(key, movement.getQuantity(), BigDecimal::add);
            sample.putIfAbsent(key, movement);
        }
        net.forEach((key, quantity) -> {
            if (quantity.signum() > 0) {
                StockMovement original = sample.get(key);
                ensureAvailable(original.getProduct(), original.getWarehouse(), quantity);
                write(original.getProduct(), original.getWarehouse(), StockMovementType.EXIT,
                        quantity.negate(), "Goods receipt cancelled", source, sourceId);
            }
        });
    }

    /**
     * Takes the goods of a delivery note out of a warehouse: one EXIT per product, owned by the
     * delivery. When the delivery comes from a confirmed order, what it delivers is taken OUT of
     * that order's reservation — a RELEASE owned by the order, marked as caused by the delivery —
     * so an order of 10 delivered 4 then 6 goes 10 reserved → 6 → 0 while the stock goes 10 → 6 → 0,
     * and what remains available to others never moves. A good that forbids negative stock must have
     * the goods on hand: what the order had reserved in this warehouse counts as available to it.
     */
    public void deliver(StockSource source, Long deliveryId, Warehouse warehouse,
                        List<StockRequirement> goods, Long orderId) {
        Map<Long, StockRequirement> merged = new LinkedHashMap<>();
        for (StockRequirement good : goods) {
            merged.merge(good.product().getId(), good,
                    (a, b) -> new StockRequirement(a.product(), a.quantity().add(b.quantity())));
        }

        List<StockMovement> held = orderId == null ? List.of()
                : movementRepository.findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(
                        currentUser.companyId(), source, orderId, RESERVATION_TYPES);

        for (StockRequirement good : merged.values()) {
            Product product = good.product();
            BigDecimal quantity = good.quantity();

            // What the order still holds of this product, warehouse by warehouse — this one first
            Map<Long, BigDecimal> heldBy = new LinkedHashMap<>();
            Map<Long, Warehouse> warehouses = new HashMap<>();
            for (StockMovement movement : held) {
                if (movement.getProduct().getId().equals(product.getId())) {
                    heldBy.merge(movement.getWarehouse().getId(), movement.getQuantity(), BigDecimal::add);
                    warehouses.putIfAbsent(movement.getWarehouse().getId(), movement.getWarehouse());
                }
            }
            List<Long> order = new ArrayList<>(heldBy.keySet());
            order.sort(Comparator.comparing(id -> !id.equals(warehouse.getId())));

            Map<Long, BigDecimal> consumed = new LinkedHashMap<>();
            BigDecimal left = quantity;
            for (Long warehouseId : order) {
                BigDecimal take = left.min(heldBy.get(warehouseId).max(BigDecimal.ZERO));
                if (take.signum() > 0) {
                    consumed.put(warehouseId, take);
                    left = left.subtract(take);
                }
            }

            ensureAvailable(product, warehouse, quantity, consumed.getOrDefault(warehouse.getId(), BigDecimal.ZERO));
            write(product, warehouse, StockMovementType.EXIT, quantity.negate(), "Delivered", source, deliveryId);
            consumed.forEach((warehouseId, take) -> write(product, warehouses.get(warehouseId),
                    StockMovementType.RELEASE, take.negate(), "Delivered", source, orderId, deliveryId));
        }
    }

    /**
     * Cancels a delivery: the goods come back (an ENTRY for what its EXITs took) and, if the order it
     * came from is still confirmed, the slice of reservation it had consumed is put back (a RESERVE).
     * A cancelled order has nothing to reserve any more, so nothing is restored there.
     */
    public void undoDelivery(StockSource source, Long deliveryId, boolean restoreReservation) {
        Map<String, BigDecimal> taken = new LinkedHashMap<>();
        Map<String, StockMovement> sample = new HashMap<>();
        for (StockMovement movement : movementRepository.findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(
                currentUser.companyId(), source, deliveryId, EnumSet.of(StockMovementType.EXIT, StockMovementType.ENTRY))) {
            String key = movement.getProduct().getId() + ":" + movement.getWarehouse().getId();
            taken.merge(key, movement.getQuantity(), BigDecimal::add);
            sample.putIfAbsent(key, movement);
        }
        taken.forEach((key, quantity) -> {
            if (quantity.signum() < 0) {
                StockMovement original = sample.get(key);
                write(original.getProduct(), original.getWarehouse(), StockMovementType.ENTRY,
                        quantity.negate(), "Delivery cancelled", source, deliveryId);
            }
        });

        if (!restoreReservation) {
            return;
        }
        Map<String, BigDecimal> released = new LinkedHashMap<>();
        Map<String, StockMovement> releasedSample = new HashMap<>();
        for (StockMovement movement : movementRepository.findByCompanyIdAndSourceTypeAndOriginIdAndTypeIn(
                currentUser.companyId(), source, deliveryId, RESERVATION_TYPES)) {
            String key = movement.getSourceId() + ":" + movement.getProduct().getId() + ":" + movement.getWarehouse().getId();
            released.merge(key, movement.getQuantity(), BigDecimal::add);
            releasedSample.putIfAbsent(key, movement);
        }
        released.forEach((key, quantity) -> {
            if (quantity.signum() < 0) {
                StockMovement original = releasedSample.get(key);
                write(original.getProduct(), original.getWarehouse(), StockMovementType.RESERVE,
                        quantity.negate(), "Reservation restored", source, original.getSourceId(), deliveryId);
            }
        });
    }

    /** Gives back whatever a document still holds — what it reserved minus what was already released. */
    public void release(StockSource source, Long sourceId) {
        List<StockMovement> held = movementRepository.findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(
                currentUser.companyId(), source, sourceId, RESERVATION_TYPES);

        Map<String, BigDecimal> net = new LinkedHashMap<>();
        Map<String, StockMovement> sample = new HashMap<>();
        for (StockMovement movement : held) {
            String key = movement.getProduct().getId() + ":" + movement.getWarehouse().getId();
            net.merge(key, movement.getQuantity(), BigDecimal::add);
            sample.putIfAbsent(key, movement);
        }
        net.forEach((key, quantity) -> {
            if (quantity.signum() > 0) {
                StockMovement original = sample.get(key);
                write(original.getProduct(), original.getWarehouse(), StockMovementType.RELEASE,
                        quantity.negate(), "Released", source, sourceId);
            }
        });
    }

    // ----- helpers -----

    private StockMovement write(Product product, Warehouse warehouse, StockMovementType type,
                                BigDecimal signedQuantity, String reason,
                                StockSource source, Long sourceId) {
        return write(product, warehouse, type, signedQuantity, reason, source, sourceId, null);
    }

    private StockMovement write(Product product, Warehouse warehouse, StockMovementType type,
                                BigDecimal signedQuantity, String reason,
                                StockSource source, Long sourceId, Long originId) {
        StockMovement movement = new StockMovement();
        movement.setCompany(companyService.currentReference());
        movement.setProduct(product);
        movement.setWarehouse(warehouse);
        movement.setType(type);
        movement.setQuantity(signedQuantity);
        movement.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        movement.setSourceType(source);
        movement.setSourceId(sourceId);
        movement.setOriginId(originId);
        movement.setOccurredAt(LocalDateTime.now());
        return movementRepository.save(movement);
    }

    /**
     * A good that forbids negative stock cannot go below zero available (what is physically
     * there minus what is already promised) in the warehouse the movement touches.
     */
    private void ensureAvailable(Product product, Warehouse warehouse, BigDecimal needed) {
        ensureAvailable(product, warehouse, needed, BigDecimal.ZERO);
    }

    /**
     * @param credit stock the caller already holds a reservation on in this warehouse — a delivery of
     *               a confirmed order may take what that order reserved, which is not "available" to others
     */
    private void ensureAvailable(Product product, Warehouse warehouse, BigDecimal needed, BigDecimal credit) {
        if (product.isAllowNegativeStock()) {
            return;
        }
        // Serialize with any concurrent movement of this good, so the level read below is still true when we write
        productService.lockForStock(product.getId());
        BigDecimal available = levelsOf(List.of(product.getId()), warehouse.getId())
                .getOrDefault(product.getId(), Levels.NONE).available().add(credit);
        if (available.compareTo(needed) < 0) {
            throw new BusinessRuleException("Not enough stock of \"" + product.getName() + "\" in "
                    + warehouse.getName() + ": " + available.stripTrailingZeros().toPlainString()
                    + " available, " + needed.stripTrailingZeros().toPlainString() + " needed");
        }
    }

    private Map<Long, Levels> levelsOf(Collection<Long> productIds, Long warehouseId) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal[]> sums = new HashMap<>(); // [physical, reserved]
        for (Object[] row : movementRepository.sumByProductAndType(currentUser.companyId(), productIds, warehouseId)) {
            Long productId = (Long) row[0];
            StockMovementType type = (StockMovementType) row[1];
            BigDecimal sum = (BigDecimal) row[2];
            BigDecimal[] pair = sums.computeIfAbsent(productId, id -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            int slot = type.isPhysical() ? 0 : 1;
            pair[slot] = pair[slot].add(sum);
        }
        Map<Long, Levels> result = new HashMap<>();
        sums.forEach((productId, pair) -> result.put(productId, new Levels(pair[0], pair[1])));
        return result;
    }

    private static BigDecimal requirePositive(BigDecimal quantity) {
        if (quantity.signum() <= 0) {
            throw new BusinessRuleException("Quantity must be greater than zero");
        }
        return quantity;
    }

    /** What a product has: physically in the warehouse and promised to customers. */
    private record Levels(BigDecimal physical, BigDecimal reserved) {

        static final Levels NONE = new Levels(BigDecimal.ZERO, BigDecimal.ZERO);

        BigDecimal available() {
            return physical.subtract(reserved);
        }
    }
}
