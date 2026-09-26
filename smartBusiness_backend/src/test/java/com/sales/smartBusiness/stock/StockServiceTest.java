package com.sales.smartBusiness.stock;

import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.product.Product;
import com.sales.smartBusiness.product.ProductService;
import com.sales.smartBusiness.product.ProductUnit;
import com.sales.smartBusiness.security.CurrentUser;
import com.sales.smartBusiness.warehouse.Warehouse;
import com.sales.smartBusiness.warehouse.WarehouseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    private static final Long COMPANY_ID = 7L;

    @Mock private StockMovementRepository movementRepository;
    @Mock private ProductService productService;
    @Mock private WarehouseService warehouseService;
    @Mock private CompanyService companyService;
    @Mock private CurrentUser currentUser;
    @Spy private StockMovementMapper movementMapper = Mappers.getMapper(StockMovementMapper.class);

    @InjectMocks private StockService service;

    private Product product;
    private Warehouse main;
    private Warehouse annex;

    @BeforeEach
    void setUp() {
        product = product(20L, "USB-C Cable", true);
        main = warehouse(1L, "Main");
        annex = warehouse(2L, "Annex");

        lenient().when(currentUser.companyId()).thenReturn(COMPANY_ID);
        lenient().when(companyService.currentReference()).thenReturn(new Company());
        lenient().when(productService.getStockable(20L)).thenReturn(product);
        lenient().when(warehouseService.getAssignable(1L)).thenReturn(main);
        lenient().when(warehouseService.getAssignable(2L)).thenReturn(annex);
        lenient().when(warehouseService.getDefault()).thenReturn(main);
        lenient().when(movementRepository.save(any(StockMovement.class))).thenAnswer(call -> call.getArgument(0));
        lenient().when(movementRepository.sumByProductAndType(any(), any(), any())).thenReturn(List.of());
    }

    // ----- Fixtures -----

    private static Product product(long id, String name, boolean allowNegative) {
        Product product = new Product();
        product.setId(id);
        product.setReference("P-" + id);
        product.setName(name);
        product.setUnit(ProductUnit.PIECE);
        product.setAllowNegativeStock(allowNegative);
        return product;
    }

    private static Warehouse warehouse(long id, String name) {
        Warehouse warehouse = new Warehouse();
        warehouse.setId(id);
        warehouse.setName(name);
        return warehouse;
    }

    /** What the register holds for the product: physical stock and a reserved quantity. */
    private void register(String physical, String reserved) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{20L, StockMovementType.ENTRY, new BigDecimal(physical)});
        if (reserved != null) {
            rows.add(new Object[]{20L, StockMovementType.RESERVE, new BigDecimal(reserved)});
        }
        lenient().when(movementRepository.sumByProductAndType(eq(COMPANY_ID), any(), any())).thenReturn(rows);
    }

    private StockMovementRequest request(StockMovementType type, String quantity) {
        StockMovementRequest request = new StockMovementRequest();
        request.setType(type);
        request.setProductId(20L);
        request.setWarehouseId(1L);
        request.setQuantity(new BigDecimal(quantity));
        request.setReason("  Inventory  ");
        return request;
    }

    private List<StockMovement> saved(int times) {
        ArgumentCaptor<StockMovement> captor = ArgumentCaptor.forClass(StockMovement.class);
        verify(movementRepository, times(times)).save(captor.capture());
        return captor.getAllValues();
    }

    // ----- Entry / exit -----

    @Test
    @DisplayName("an entry writes a positive movement, stamped and with a trimmed reason")
    void entry() {
        StockMovementResponse response = service.record(request(StockMovementType.ENTRY, "10"));

        StockMovement movement = saved(1).get(0);
        assertThat(movement.getQuantity()).isEqualByComparingTo("10");
        assertThat(movement.getType()).isEqualTo(StockMovementType.ENTRY);
        assertThat(movement.getProduct()).isSameAs(product);
        assertThat(movement.getWarehouse()).isSameAs(main);
        assertThat(movement.getReason()).isEqualTo("Inventory");
        assertThat(movement.getSourceType()).isNull();
        assertThat(movement.getOccurredAt()).isNotNull();
        assertThat(response.getProductName()).isEqualTo("USB-C Cable");
        assertThat(response.getWarehouseName()).isEqualTo("Main");
    }

    @Test
    @DisplayName("an entry or an exit of nothing is refused")
    void zeroQuantityRefused() {
        assertThatThrownBy(() -> service.record(request(StockMovementType.ENTRY, "0")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.record(request(StockMovementType.EXIT, "0")))
                .isInstanceOf(BusinessRuleException.class);

        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("an exit writes a negative movement")
    void exit() {
        register("10", null);

        service.record(request(StockMovementType.EXIT, "4"));

        assertThat(saved(1).get(0).getQuantity()).isEqualByComparingTo("-4");
    }

    @Test
    @DisplayName("an exit past the available stock is refused when the product forbids negative stock")
    void exitRefusedWhenNotEnough() {
        product.setAllowNegativeStock(false);
        register("3", null);

        assertThatThrownBy(() -> service.record(request(StockMovementType.EXIT, "4")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Not enough stock of \"USB-C Cable\" in Main: 3 available, 4 needed");

        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("stock already promised to customers is not available for an exit")
    void reservedStockIsNotAvailable() {
        product.setAllowNegativeStock(false);
        register("10", "8");

        assertThatThrownBy(() -> service.record(request(StockMovementType.EXIT, "5")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2 available");
    }

    @Test
    @DisplayName("a product that allows negative stock can be sold past zero")
    void exitAllowedWhenNegativeStockIsAllowed() {
        register("1", null);

        service.record(request(StockMovementType.EXIT, "5"));

        assertThat(saved(1).get(0).getQuantity()).isEqualByComparingTo("-5");
    }

    // ----- Adjustment -----

    @Test
    @DisplayName("an adjustment writes the difference between the count and the register")
    void adjustmentWritesTheDifference() {
        register("10", null);

        service.record(request(StockMovementType.ADJUSTMENT, "7"));

        StockMovement movement = saved(1).get(0);
        assertThat(movement.getType()).isEqualTo(StockMovementType.ADJUSTMENT);
        assertThat(movement.getQuantity()).isEqualByComparingTo("-3");
    }

    @Test
    @DisplayName("a count higher than the register adds the surplus")
    void adjustmentUpwards() {
        register("10", null);

        service.record(request(StockMovementType.ADJUSTMENT, "12.5"));

        assertThat(saved(1).get(0).getQuantity()).isEqualByComparingTo("2.5");
    }

    @Test
    @DisplayName("a count that matches the register writes nothing")
    void adjustmentWithNoDifferenceRefused() {
        register("10", null);

        assertThatThrownBy(() -> service.record(request(StockMovementType.ADJUSTMENT, "10")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("nothing to adjust");

        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("counting zero on an empty shelf is nothing to adjust either")
    void adjustmentOnEmptyRegister() {
        assertThatThrownBy(() -> service.record(request(StockMovementType.ADJUSTMENT, "0")))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("reservations and transfers cannot be recorded by hand")
    void onlyManualTypesAllowed() {
        for (StockMovementType type : List.of(StockMovementType.RESERVE, StockMovementType.RELEASE,
                StockMovementType.TRANSFER_IN, StockMovementType.TRANSFER_OUT)) {
            assertThatThrownBy(() -> service.record(request(type, "1")))
                    .isInstanceOf(BusinessRuleException.class);
        }
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("a service, or a product of another company, is refused before anything is written")
    void productMustBeStockable() {
        when(productService.getStockable(20L)).thenThrow(new BusinessRuleException("\"Consulting\" is a service: it has no stock"));

        assertThatThrownBy(() -> service.record(request(StockMovementType.ENTRY, "1")))
                .isInstanceOf(BusinessRuleException.class);

        verify(movementRepository, never()).save(any());
    }

    // ----- Transfer -----

    private StockTransferRequest transfer(long from, long to, String quantity) {
        StockTransferRequest request = new StockTransferRequest();
        request.setProductId(20L);
        request.setFromWarehouseId(from);
        request.setToWarehouseId(to);
        request.setQuantity(new BigDecimal(quantity));
        return request;
    }

    @Test
    @DisplayName("a transfer writes an exit from the source and an entry into the destination")
    void transferWritesTwoLegs() {
        register("10", null);

        List<StockMovementResponse> legs = service.transfer(transfer(1, 2, "4"));

        List<StockMovement> written = saved(2);
        assertThat(written.get(0).getType()).isEqualTo(StockMovementType.TRANSFER_OUT);
        assertThat(written.get(0).getWarehouse()).isSameAs(main);
        assertThat(written.get(0).getQuantity()).isEqualByComparingTo("-4");
        assertThat(written.get(1).getType()).isEqualTo(StockMovementType.TRANSFER_IN);
        assertThat(written.get(1).getWarehouse()).isSameAs(annex);
        assertThat(written.get(1).getQuantity()).isEqualByComparingTo("4");
        assertThat(legs).hasSize(2);
    }

    @Test
    @DisplayName("a transfer needs two different warehouses")
    void transferNeedsTwoWarehouses() {
        assertThatThrownBy(() -> service.transfer(transfer(1, 1, "4")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("two different warehouses");
    }

    @Test
    @DisplayName("a transfer cannot take more than the source has available")
    void transferRefusedWhenNotEnough() {
        product.setAllowNegativeStock(false);
        register("3", null);

        assertThatThrownBy(() -> service.transfer(transfer(1, 2, "4")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Not enough stock");

        verify(movementRepository, never()).save(any());
    }

    // ----- Reserve / release -----

    @Test
    @DisplayName("a reservation is written in the default warehouse, linked to its document")
    void reserve() {
        service.reserve(StockSource.SALES_DOCUMENT, 5L, List.of(new StockRequirement(product, new BigDecimal("3"))));

        StockMovement movement = saved(1).get(0);
        assertThat(movement.getType()).isEqualTo(StockMovementType.RESERVE);
        assertThat(movement.getQuantity()).isEqualByComparingTo("3");
        assertThat(movement.getWarehouse()).isSameAs(main);
        assertThat(movement.getSourceType()).isEqualTo(StockSource.SALES_DOCUMENT);
        assertThat(movement.getSourceId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("two lines for the same product are reserved as one quantity")
    void reserveMergesDuplicateLines() {
        service.reserve(StockSource.SALES_DOCUMENT, 5L, List.of(
                new StockRequirement(product, new BigDecimal("3")),
                new StockRequirement(product, new BigDecimal("2"))));

        assertThat(saved(1).get(0).getQuantity()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("a reservation is refused, naming the product, when a strict product lacks stock")
    void reserveRefusedWhenNotEnough() {
        product.setAllowNegativeStock(false);
        register("2", null);

        assertThatThrownBy(() -> service.reserve(StockSource.SALES_DOCUMENT, 5L,
                List.of(new StockRequirement(product, new BigDecimal("3")))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("USB-C Cable");

        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("nothing to reserve does not even look for a warehouse")
    void reserveNothing() {
        service.reserve(StockSource.SALES_DOCUMENT, 5L, List.of());

        verifyNoInteractions(warehouseService);
        verify(movementRepository, never()).save(any());
    }

    private StockMovement held(StockMovementType type, String quantity) {
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setWarehouse(main);
        movement.setType(type);
        movement.setQuantity(new BigDecimal(quantity));
        return movement;
    }

    @Test
    @DisplayName("a release gives back exactly what the document still holds")
    void release() {
        when(movementRepository.findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(
                eq(COMPANY_ID), eq(StockSource.SALES_DOCUMENT), eq(5L), any()))
                .thenReturn(List.of(held(StockMovementType.RESERVE, "5"), held(StockMovementType.RELEASE, "-2")));

        service.release(StockSource.SALES_DOCUMENT, 5L);

        StockMovement movement = saved(1).get(0);
        assertThat(movement.getType()).isEqualTo(StockMovementType.RELEASE);
        assertThat(movement.getQuantity()).isEqualByComparingTo("-3");
        assertThat(movement.getSourceId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("a document that holds nothing releases nothing")
    void releaseNothing() {
        when(movementRepository.findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(any(), any(), any(), any()))
                .thenReturn(List.of(held(StockMovementType.RESERVE, "5"), held(StockMovementType.RELEASE, "-5")));

        service.release(StockSource.SALES_DOCUMENT, 5L);

        verify(movementRepository, never()).save(any());
    }

    // ----- Receipts (purchases) -----

    @Test
    @DisplayName("a receipt puts each product into its warehouse, linked to the document")
    void receive() {
        service.receive(StockSource.PURCHASE_DOCUMENT, 9L, annex,
                List.of(new StockRequirement(product, new BigDecimal("6"))));

        StockMovement movement = saved(1).get(0);
        assertThat(movement.getType()).isEqualTo(StockMovementType.ENTRY);
        assertThat(movement.getQuantity()).isEqualByComparingTo("6");
        assertThat(movement.getWarehouse()).isSameAs(annex);
        assertThat(movement.getSourceType()).isEqualTo(StockSource.PURCHASE_DOCUMENT);
        assertThat(movement.getSourceId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("two lines for the same product are received as one entry")
    void receiveMergesLines() {
        service.receive(StockSource.PURCHASE_DOCUMENT, 9L, main, List.of(
                new StockRequirement(product, new BigDecimal("6")),
                new StockRequirement(product, new BigDecimal("4"))));

        assertThat(saved(1).get(0).getQuantity()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("cancelling a receipt writes the opposite exit, in the warehouse that received")
    void reverseReceipt() {
        when(movementRepository.findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(
                eq(COMPANY_ID), eq(StockSource.PURCHASE_DOCUMENT), eq(9L), any()))
                .thenReturn(List.of(held(StockMovementType.ENTRY, "6")));

        service.reverseReceipt(StockSource.PURCHASE_DOCUMENT, 9L);

        StockMovement movement = saved(1).get(0);
        assertThat(movement.getType()).isEqualTo(StockMovementType.EXIT);
        assertThat(movement.getQuantity()).isEqualByComparingTo("-6");
        assertThat(movement.getWarehouse()).isSameAs(main);
        assertThat(movement.getSourceId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("a receipt already undone is not undone twice")
    void reverseReceiptOnlyOnce() {
        when(movementRepository.findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(any(), any(), any(), any()))
                .thenReturn(List.of(held(StockMovementType.ENTRY, "6"), held(StockMovementType.EXIT, "-6")));

        service.reverseReceipt(StockSource.PURCHASE_DOCUMENT, 9L);

        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("a receipt cannot be cancelled once a strict product's goods are gone")
    void reverseReceiptRefusedWhenGoodsAreGone() {
        product.setAllowNegativeStock(false);
        register("2", null); // 6 came in, only 2 are left
        when(movementRepository.findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(any(), any(), any(), any()))
                .thenReturn(List.of(held(StockMovementType.ENTRY, "6")));

        assertThatThrownBy(() -> service.reverseReceipt(StockSource.PURCHASE_DOCUMENT, 9L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Not enough stock");

        verify(movementRepository, never()).save(any());
    }

    // ----- Levels -----

    @Test
    @DisplayName("levels show physical, reserved and available, and flag a product at its minimum")
    void levels() {
        product.setMinStock(new BigDecimal("5"));
        when(productService.searchGoods(any(), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(product)));
        register("10", "6");

        StockLevelResponse level = service.levels("cable", null, false, PageRequest.of(0, 10)).getContent().get(0);

        assertThat(level.getPhysical()).isEqualByComparingTo("10");
        assertThat(level.getReserved()).isEqualByComparingTo("6");
        assertThat(level.getAvailable()).isEqualByComparingTo("4");
        assertThat(level.isLowStock()).isTrue();
    }

    @Test
    @DisplayName("a product without a minimum is never low")
    void noMinimumNoAlert() {
        when(productService.searchGoods(any(), eq(false), any())).thenReturn(new PageImpl<>(List.of(product)));

        StockLevelResponse level = service.levels(null, null, false, PageRequest.of(0, 10)).getContent().get(0);

        assertThat(level.getAvailable()).isEqualByComparingTo("0");
        assertThat(level.isLowStock()).isFalse();
    }

    @Test
    @DisplayName("viewing one warehouse shows its figures, without the company-wide low-stock flag or filter")
    void warehouseViewHasNoLowStockFlag() {
        product.setMinStock(new BigDecimal("50"));
        when(productService.searchGoods(any(), eq(false), any())).thenReturn(new PageImpl<>(List.of(product)));
        register("10", null);

        StockLevelResponse level = service.levels(null, 1L, true, PageRequest.of(0, 10)).getContent().get(0);

        assertThat(level.getPhysical()).isEqualByComparingTo("10");
        assertThat(level.isLowStock()).isFalse();
        verify(warehouseService).findById(1L);
        verify(movementRepository).sumByProductAndType(COMPANY_ID, List.of(20L), 1L);
    }

    // ----- Deliveries (sales) -----

    private StockMovement reservation(StockMovementType type, String quantity, long orderId, Warehouse where) {
        StockMovement movement = new StockMovement();
        movement.setProduct(product);
        movement.setWarehouse(where);
        movement.setType(type);
        movement.setQuantity(new BigDecimal(quantity));
        movement.setSourceType(StockSource.SALES_DOCUMENT);
        movement.setSourceId(orderId);
        return movement;
    }

    private void orderHolds(long orderId, StockMovement... rows) {
        when(movementRepository.findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(
                eq(COMPANY_ID), eq(StockSource.SALES_DOCUMENT), eq(orderId), any())).thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("a delivery without an order takes the goods out, and nothing else")
    void deliverWithoutOrder() {
        service.deliver(StockSource.SALES_DOCUMENT, 30L, main, List.of(new StockRequirement(product, new BigDecimal("4"))), null);

        StockMovement exit = saved(1).get(0);
        assertThat(exit.getType()).isEqualTo(StockMovementType.EXIT);
        assertThat(exit.getQuantity()).isEqualByComparingTo("-4");
        assertThat(exit.getSourceId()).isEqualTo(30L);
        assertThat(exit.getWarehouse()).isSameAs(main);
    }

    @Test
    @DisplayName("a delivery out of a confirmed order releases the reservation it consumes, marked as caused by the delivery")
    void deliverConsumesReservation() {
        orderHolds(8L, reservation(StockMovementType.RESERVE, "10", 8L, main));

        service.deliver(StockSource.SALES_DOCUMENT, 30L, main, List.of(new StockRequirement(product, new BigDecimal("4"))), 8L);

        List<StockMovement> written = saved(2);
        StockMovement exit = written.get(0);
        StockMovement release = written.get(1);
        assertThat(exit.getType()).isEqualTo(StockMovementType.EXIT);
        assertThat(exit.getQuantity()).isEqualByComparingTo("-4");
        assertThat(exit.getSourceId()).isEqualTo(30L);
        assertThat(release.getType()).isEqualTo(StockMovementType.RELEASE);
        assertThat(release.getQuantity()).isEqualByComparingTo("-4");
        assertThat(release.getSourceId()).isEqualTo(8L);      // the order owns the reservation…
        assertThat(release.getOriginId()).isEqualTo(30L);     // …the delivery consumed it
    }

    @Test
    @DisplayName("what an earlier delivery already consumed is not consumed twice: 10 ordered, 4 delivered, 6 left to consume")
    void deliverConsumesOnlyWhatIsStillHeld() {
        orderHolds(8L, reservation(StockMovementType.RESERVE, "10", 8L, main),
                reservation(StockMovementType.RELEASE, "-4", 8L, main));

        service.deliver(StockSource.SALES_DOCUMENT, 31L, main, List.of(new StockRequirement(product, new BigDecimal("8"))), 8L);

        List<StockMovement> written = saved(2);
        assertThat(written.get(0).getQuantity()).isEqualByComparingTo("-8");   // 8 leave the shelf…
        assertThat(written.get(1).getQuantity()).isEqualByComparingTo("-6");   // …but only 6 were reserved
    }

    @Test
    @DisplayName("a strict product may take what its own order reserved, but not what is reserved for others")
    void deliverAvailabilityCountsTheOrdersOwnReservation() {
        product.setAllowNegativeStock(false);
        register("10", "10"); // 10 on the shelf, all of it reserved (by this order)
        orderHolds(8L, reservation(StockMovementType.RESERVE, "10", 8L, main));

        service.deliver(StockSource.SALES_DOCUMENT, 30L, main, List.of(new StockRequirement(product, new BigDecimal("10"))), 8L);

        saved(2);
    }

    @Test
    @DisplayName("a strict product is refused a delivery that would eat into another order's reservation")
    void deliverRefusedWhenTheGoodsAreReservedForOthers() {
        product.setAllowNegativeStock(false);
        register("10", "10");
        orderHolds(8L, reservation(StockMovementType.RESERVE, "4", 8L, main)); // this order holds only 4 of the 10 reserved

        assertThatThrownBy(() -> service.deliver(StockSource.SALES_DOCUMENT, 30L, main,
                List.of(new StockRequirement(product, new BigDecimal("6"))), 8L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Not enough stock");

        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("a strict product without the goods cannot be delivered")
    void deliverRefusedWhenNotEnough() {
        product.setAllowNegativeStock(false);
        register("2", null);

        assertThatThrownBy(() -> service.deliver(StockSource.SALES_DOCUMENT, 30L, main,
                List.of(new StockRequirement(product, new BigDecimal("3"))), null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("2 available");
    }

    @Test
    @DisplayName("two lines of the same product are delivered as one exit")
    void deliverMergesLines() {
        service.deliver(StockSource.SALES_DOCUMENT, 30L, main, List.of(
                new StockRequirement(product, new BigDecimal("3")),
                new StockRequirement(product, new BigDecimal("2"))), null);

        assertThat(saved(1).get(0).getQuantity()).isEqualByComparingTo("-5");
    }

    @Test
    @DisplayName("cancelling a delivery puts the goods back and restores the reservation it had consumed")
    void undoDelivery() {
        StockMovement exit = held(StockMovementType.EXIT, "-4");
        when(movementRepository.findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(
                eq(COMPANY_ID), eq(StockSource.SALES_DOCUMENT), eq(30L), any())).thenReturn(List.of(exit));
        StockMovement release = reservation(StockMovementType.RELEASE, "-4", 8L, main);
        release.setOriginId(30L);
        when(movementRepository.findByCompanyIdAndSourceTypeAndOriginIdAndTypeIn(
                eq(COMPANY_ID), eq(StockSource.SALES_DOCUMENT), eq(30L), any())).thenReturn(List.of(release));

        service.undoDelivery(StockSource.SALES_DOCUMENT, 30L, true);

        List<StockMovement> written = saved(2);
        assertThat(written.get(0).getType()).isEqualTo(StockMovementType.ENTRY);
        assertThat(written.get(0).getQuantity()).isEqualByComparingTo("4");
        assertThat(written.get(0).getSourceId()).isEqualTo(30L);
        assertThat(written.get(1).getType()).isEqualTo(StockMovementType.RESERVE);
        assertThat(written.get(1).getQuantity()).isEqualByComparingTo("4");
        assertThat(written.get(1).getSourceId()).isEqualTo(8L);   // back on the order that held it
    }

    @Test
    @DisplayName("the reservation is left alone when the order no longer stands")
    void undoDeliveryWithoutRestoringTheReservation() {
        when(movementRepository.findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(any(), any(), eq(30L), any()))
                .thenReturn(List.of(held(StockMovementType.EXIT, "-4")));

        service.undoDelivery(StockSource.SALES_DOCUMENT, 30L, false);

        assertThat(saved(1).get(0).getType()).isEqualTo(StockMovementType.ENTRY);
        verify(movementRepository, never()).findByCompanyIdAndSourceTypeAndOriginIdAndTypeIn(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a delivery already undone is not undone twice")
    void undoDeliveryOnlyOnce() {
        when(movementRepository.findByCompanyIdAndSourceTypeAndSourceIdAndTypeIn(any(), any(), eq(30L), any()))
                .thenReturn(List.of(held(StockMovementType.EXIT, "-4"), held(StockMovementType.ENTRY, "4")));
        StockMovement release = reservation(StockMovementType.RELEASE, "-4", 8L, main);
        StockMovement restored = reservation(StockMovementType.RESERVE, "4", 8L, main);
        when(movementRepository.findByCompanyIdAndSourceTypeAndOriginIdAndTypeIn(any(), any(), eq(30L), any()))
                .thenReturn(List.of(release, restored));

        service.undoDelivery(StockSource.SALES_DOCUMENT, 30L, true);

        verify(movementRepository, never()).save(any());
    }
}
