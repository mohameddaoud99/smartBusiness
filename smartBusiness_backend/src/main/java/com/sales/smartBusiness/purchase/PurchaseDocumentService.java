package com.sales.smartBusiness.purchase;

import com.sales.smartBusiness.common.MonthAmount;
import com.sales.smartBusiness.common.SearchPattern;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.exception.BusinessRuleException;
import com.sales.smartBusiness.exception.ResourceNotFoundException;
import com.sales.smartBusiness.numbering.NumberingService;
import com.sales.smartBusiness.product.Product;
import com.sales.smartBusiness.product.ProductKind;
import com.sales.smartBusiness.product.ProductService;
import com.sales.smartBusiness.security.CurrentUser;
import com.sales.smartBusiness.stock.StockRequirement;
import com.sales.smartBusiness.stock.StockService;
import com.sales.smartBusiness.stock.StockSource;
import com.sales.smartBusiness.supplier.Supplier;
import com.sales.smartBusiness.supplier.SupplierService;
import com.sales.smartBusiness.tax.Tax;
import com.sales.smartBusiness.tax.TaxKind;
import com.sales.smartBusiness.tax.TaxService;
import com.sales.smartBusiness.warehouse.Warehouse;
import com.sales.smartBusiness.warehouse.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Purchase orders and goods receipts — the mirror of {@code SalesDocumentService}: same draft →
 * numbered-at-validation → frozen life, same calculation ({@code DocumentTotals}). What differs
 * is the third party (a supplier), the price (the purchase price) and the stock: validating a
 * goods receipt puts its goods INTO a warehouse, and cancelling it takes them back out.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PurchaseDocumentService {

    private final PurchaseDocumentRepository documentRepository;
    private final CompanyService companyService;
    private final SupplierService supplierService;
    private final ProductService productService;
    private final TaxService taxService;
    private final NumberingService numberingService;
    private final WarehouseService warehouseService;
    private final StockService stockService;
    private final PurchaseDocumentMapper documentMapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public Page<PurchaseDocumentSummaryResponse> search(PurchaseDocumentType type, String search,
                                                        PurchaseDocumentStatus status, Long supplierId,
                                                        Pageable pageable) {
        return documentRepository.search(currentUser.companyId(), type, SearchPattern.like(search),
                        status, supplierId, pageable)
                .map(documentMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public PurchaseDocumentResponse findById(Long id) {
        return toResponse(getDocument(id));
    }

    public PurchaseDocumentResponse create(PurchaseDocumentRequest request) {
        if (request.getType() == PurchaseDocumentType.PURCHASE_CREDIT_NOTE) {
            throw new BusinessRuleException(
                    "A supplier credit note is made from an invoice: open the invoice and create it from there");
        }
        checkDates(request);
        Supplier supplier = supplierService.getAssignable(request.getSupplierId());
        Warehouse warehouse = resolveWarehouse(request);
        References references = resolveReferences(request);

        PurchaseDocument document = documentMapper.toEntity(request);
        document.setCompany(companyService.currentReference());
        document.setSupplier(supplier);
        document.setWarehouse(warehouse);
        fill(document, request, references);

        return toResponse(documentRepository.save(document));
    }

    public PurchaseDocumentResponse update(Long id, PurchaseDocumentRequest request) {
        PurchaseDocument document = getDocument(id);
        requireDraft(document, "edited");
        if (request.getType() != document.getType()) {
            throw new BusinessRuleException("A document's type cannot be changed");
        }
        if (document.getType() == PurchaseDocumentType.PURCHASE_CREDIT_NOTE
                && !document.getSupplier().getId().equals(request.getSupplierId())) {
            throw new BusinessRuleException("A supplier credit note stays with the supplier of its invoice");
        }
        if (document.getType() == PurchaseDocumentType.PURCHASE_RETURN_NOTE && document.getSource() != null
                && !document.getSupplier().getId().equals(request.getSupplierId())) {
            throw new BusinessRuleException("A return note stays with the supplier of the document it was made from");
        }
        checkDates(request);
        Supplier supplier = supplierService.getAssignable(request.getSupplierId());
        Warehouse warehouse = resolveWarehouse(request);
        References references = resolveReferences(request);

        documentMapper.updateEntity(request, document);
        document.setSupplier(supplier);
        document.setWarehouse(warehouse);
        fill(document, request, references);

        return toResponse(document);
    }

    /** Only a draft can go: a validated document is cancelled, never erased. */
    public void delete(Long id) {
        PurchaseDocument document = getDocument(id);
        requireDraft(document, "deleted");
        documentRepository.delete(document);
    }

    /**
     * Freezes a draft: the number is allocated now — not when the draft was started — so a
     * discarded draft leaves no gap. A goods receipt - and an invoice that is not made from one - also puts
     * its goods into the stock, in the same transaction: either both happen or neither.
     */
    public PurchaseDocumentResponse validate(Long id) {
        PurchaseDocument document = getDocument(id);
        requireDraft(document, "validated");
        checkAgainstSource(document);

        document.setReference(nextFreeReference(document.getType()));
        document.setStatus(PurchaseDocumentStatus.VALIDATED);
        if (receivesGoods(document)) {
            // The goods arrive: refused, the document stays a draft and its number is given back
            stockService.receive(StockSource.PURCHASE_DOCUMENT, document.getId(),
                    document.getWarehouse(), goodsOf(document));
        }
        if (document.getType() == PurchaseDocumentType.PURCHASE_RETURN_NOTE) {
            // The goods go back to the supplier: refused - and the note stays a draft, its number given back -
            // when a strict product does not have them in that warehouse
            stockService.deliver(StockSource.PURCHASE_DOCUMENT, document.getId(), document.getWarehouse(),
                    goodsOf(document), null);
        }
        if (document.getType() == PurchaseDocumentType.PURCHASE_CREDIT_NOTE) {
            applyToInvoice(document);
        }
        return toResponse(document);
    }

    /**
     * Cancels a validated document. A goods receipt takes its goods back out of the stock (refused
     * when a strict product has already been sold or reserved); a purchase order that still has
     * a live receipt cannot be cancelled — cancel the receipts first.
     */
    public PurchaseDocumentResponse cancel(Long id) {
        PurchaseDocument document = getDocument(id);
        if (document.isDraft()) {
            throw new BusinessRuleException("A draft is deleted, not cancelled");
        }
        if (document.getType() == PurchaseDocumentType.PURCHASE_INVOICE && document.getPaidAmount().signum() > 0) {
            throw new BusinessRuleException("This invoice has payments. Cancel them before cancelling the invoice.");
        }
        if (document.getType() == PurchaseDocumentType.PURCHASE_INVOICE
                && hasLiveChild(document, PurchaseDocumentType.PURCHASE_CREDIT_NOTE)) {
            throw new BusinessRuleException("This invoice has credit notes. Cancel them before cancelling the invoice.");
        }
        if ((document.getType() == PurchaseDocumentType.GOODS_RECEIPT
                || document.getType() == PurchaseDocumentType.PURCHASE_INVOICE)
                && hasLiveChild(document, PurchaseDocumentType.PURCHASE_RETURN_NOTE)) {
            // Cancelling it would take the goods out of the stock a second time
            throw new BusinessRuleException("This " + document.getType().getLabel().toLowerCase()
                    + " has a return note. Cancel the return note before cancelling it.");
        }
        if (!document.canMoveTo(PurchaseDocumentStatus.CANCELLED)) {
            throw new BusinessRuleException("A " + document.getType().getLabel().toLowerCase()
                    + " that is " + document.getStatus().name().toLowerCase() + " cannot be cancelled");
        }
        if (document.getType() == PurchaseDocumentType.PURCHASE_ORDER
                && hasLiveChild(document, PurchaseDocumentType.GOODS_RECEIPT)) {
            throw new BusinessRuleException(
                    "This purchase order has goods receipts. Cancel them before cancelling the order.");
        }
        if (isInvoiceable(document) && hasLiveChild(document, PurchaseDocumentType.PURCHASE_INVOICE)) {
            throw new BusinessRuleException("This " + document.getType().getLabel().toLowerCase()
                    + " has an invoice. Cancel the invoice before cancelling it.");
        }

        document.setStatus(PurchaseDocumentStatus.CANCELLED);
        if (receivesGoods(document)) {
            stockService.reverseReceipt(StockSource.PURCHASE_DOCUMENT, document.getId());
        }
        if (document.getType() == PurchaseDocumentType.PURCHASE_RETURN_NOTE) {
            // The goods that went back to the supplier come back into the stock
            stockService.undoDelivery(StockSource.PURCHASE_DOCUMENT, document.getId(), false);
        }
        if (document.getType() == PurchaseDocumentType.PURCHASE_CREDIT_NOTE) {
            // The invoice asks us for that amount again
            PurchaseDocument invoice = document.getSource();
            invoice.applyCredited(creditedOn(invoice));
        }
        return toResponse(document);
    }

    /**
     * Makes a draft goods receipt from a validated purchase order, copying its lines and taxes
     * as they are. The receipt is a draft the user then adjusts to what really arrived — a
     * partial delivery is a receipt with lower quantities, and an order can have several.
     */
    public PurchaseDocumentResponse convertToReceipt(Long id) {
        PurchaseDocument order = getDocument(id);
        if (order.getType() != PurchaseDocumentType.PURCHASE_ORDER) {
            throw new BusinessRuleException("Only a purchase order can be turned into a goods receipt");
        }
        if (order.getStatus() != PurchaseDocumentStatus.VALIDATED) {
            throw new BusinessRuleException("Validate the purchase order first");
        }
        if (hasLiveChild(order, PurchaseDocumentType.PURCHASE_INVOICE)) {
            throw new BusinessRuleException("This purchase order is already invoiced: its goods came in with the invoice");
        }

        PurchaseDocument receipt = new PurchaseDocument();
        receipt.setCompany(order.getCompany());
        receipt.setType(PurchaseDocumentType.GOODS_RECEIPT);
        receipt.setSupplier(order.getSupplier());
        receipt.setWarehouse(warehouseService.getDefault());
        receipt.setSource(order);
        receipt.setIssueDate(LocalDate.now());
        receipt.copyContentFrom(order, remainingOf(order));
        if (receipt.getLines().isEmpty()) {
            throw new BusinessRuleException("Everything ordered has already been received");
        }

        return toResponse(documentRepository.save(receipt));
    }

    /**
     * Makes a draft return note from a VALIDATED goods receipt or purchase invoice - paid or not - copying its lines
     * and taxes: the user lowers the quantities to what really goes back to the supplier. Validating it takes those
     * goods out of the stock. Several return notes can be made from one document; what has been sent back is not
     * tracked line by line yet, so nothing stops returning more than was received.
     */
    public PurchaseDocumentResponse convertToReturnNote(Long id) {
        PurchaseDocument source = getDocument(id);
        if (source.getType() != PurchaseDocumentType.GOODS_RECEIPT
                && source.getType() != PurchaseDocumentType.PURCHASE_INVOICE) {
            throw new BusinessRuleException("Only a goods receipt or an invoice can be returned");
        }
        if (source.isDraft() || source.getStatus() == PurchaseDocumentStatus.CANCELLED) {
            throw new BusinessRuleException("Only a validated " + source.getType().getLabel().toLowerCase()
                    + " can be returned");
        }

        PurchaseDocument note = new PurchaseDocument();
        note.setCompany(source.getCompany());
        note.setType(PurchaseDocumentType.PURCHASE_RETURN_NOTE);
        note.setSupplier(source.getSupplier());
        note.setWarehouse(source.getWarehouse());
        note.setSource(source);
        note.setIssueDate(LocalDate.now());
        note.copyContentFrom(source, remainingOf(source));
        if (note.getLines().isEmpty()) {
            throw new BusinessRuleException("Everything has already been returned");
        }

        return toResponse(documentRepository.save(note));
    }

    /**
     * Makes a draft supplier credit note from a validated invoice - paid or not - copying its lines and taxes: the
     * user lowers them to what is really credited. Several credit notes can be made on one invoice, up to its total.
     * A credit note is a financial document: it moves no stock (goods sent back are taken out of the stock by hand
     * for now).
     */
    public PurchaseDocumentResponse convertToCreditNote(Long id) {
        PurchaseDocument invoice = getDocument(id);
        if (invoice.getType() != PurchaseDocumentType.PURCHASE_INVOICE) {
            throw new BusinessRuleException("Only an invoice can be credited");
        }
        requireCreditable(invoice);
        if (invoice.getCreditedAmount().compareTo(invoice.getTotal()) >= 0) {
            throw new BusinessRuleException("This invoice is already credited in full");
        }

        PurchaseDocument note = new PurchaseDocument();
        note.setCompany(invoice.getCompany());
        note.setType(PurchaseDocumentType.PURCHASE_CREDIT_NOTE);
        note.setSupplier(invoice.getSupplier());
        note.setSource(invoice);
        note.setIssueDate(LocalDate.now());
        note.copyContentFrom(invoice);

        return toResponse(documentRepository.save(note));
    }

    /**
     * Makes a draft invoice from a VALIDATED purchase order or goods receipt, copying its lines and taxes as they
     * are. One live invoice per source. An order whose goods came in on receipts is invoiced through them, not
     * directly. What the invoice does to the stock is decided when it is validated: nothing when it comes from a
     * goods receipt (the goods are in already), the goods come in otherwise.
     */
    public PurchaseDocumentResponse convertToInvoice(Long id) {
        PurchaseDocument source = getDocument(id);
        switch (source.getType()) {
            case PURCHASE_ORDER -> {
                if (source.getStatus() != PurchaseDocumentStatus.VALIDATED) {
                    throw new BusinessRuleException("Validate the purchase order first");
                }
                if (hasLiveChild(source, PurchaseDocumentType.GOODS_RECEIPT)) {
                    throw new BusinessRuleException("This purchase order has goods receipts: invoice them instead");
                }
                requireNotInvoiced(source);
            }
            case GOODS_RECEIPT -> {
                if (source.getStatus() != PurchaseDocumentStatus.VALIDATED) {
                    throw new BusinessRuleException("Only a validated goods receipt can be invoiced");
                }
                requireNotInvoiced(source);
            }
            default -> throw new BusinessRuleException("A " + source.getType().getLabel().toLowerCase()
                    + " cannot be turned into an invoice");
        }

        PurchaseDocument invoice = new PurchaseDocument();
        invoice.setCompany(source.getCompany());
        invoice.setType(PurchaseDocumentType.PURCHASE_INVOICE);
        invoice.setSupplier(source.getSupplier());
        invoice.setWarehouse(source.getType() == PurchaseDocumentType.GOODS_RECEIPT
                ? source.getWarehouse() : warehouseService.getDefault());
        invoice.setSource(source);
        invoice.setIssueDate(LocalDate.now());
        invoice.copyContentFrom(source);

        return toResponse(documentRepository.save(invoice));
    }

    /**
     * Records how much of an invoice has been paid. The caller passes the SUM of its active payments, never
     * "one more", so the invoice cannot drift from them. Moves the status: unpaid, partly paid, paid.
     */
    public void applyPaid(Long invoiceId, BigDecimal paid) {
        getDocument(invoiceId).applyPaid(paid);
    }

    /** The invoice a supplier payment is about - the one place that says which invoices can still be paid. */
    @Transactional(readOnly = true)
    public PurchaseDocument getPayableInvoice(Long id) {
        PurchaseDocument invoice = getDocument(id);
        if (invoice.getType() != PurchaseDocumentType.PURCHASE_INVOICE) {
            throw new BusinessRuleException("Only an invoice can be paid");
        }
        if (invoice.isDraft()) {
            throw new BusinessRuleException("Validate the invoice before recording a payment");
        }
        if (invoice.getStatus() == PurchaseDocumentStatus.CANCELLED) {
            throw new BusinessRuleException("This invoice is cancelled");
        }
        if (invoice.getStatus() == PurchaseDocumentStatus.PAID) {
            throw new BusinessRuleException("This invoice is already paid in full");
        }
        return invoice;
    }

    /** The totals a request would produce, without saving anything — for the live editor. */
    @Transactional(readOnly = true)
    public PurchaseDocumentResponse preview(PurchaseDocumentRequest request) {
        checkDates(request);
        References references = resolveReferences(request);

        PurchaseDocument document = documentMapper.toEntity(request);
        fill(document, request, references);
        return documentMapper.toResponse(document);
    }

    // ----- Quantities followed line by line -----

    /** A document that takes quantities off the lines of another: which type, which of its statuses count, what it does. */
    private record Tracking(PurchaseDocumentType taker, List<PurchaseDocumentStatus> counting, String verb) {
    }

    /**
     * What is followed line by line: a purchase order is received on goods receipts (validated ones count); a goods
     * receipt or an invoice is returned on return notes (validated ones count).
     */
    private static Tracking trackingOf(PurchaseDocumentType source) {
        return switch (source) {
            case PURCHASE_ORDER -> new Tracking(PurchaseDocumentType.GOODS_RECEIPT,
                    List.of(PurchaseDocumentStatus.VALIDATED), "receive");
            case GOODS_RECEIPT, PURCHASE_INVOICE -> new Tracking(PurchaseDocumentType.PURCHASE_RETURN_NOTE,
                    List.of(PurchaseDocumentStatus.VALIDATED), "return");
            default -> null;
        };
    }

    /** What the documents of the tracked type have taken of each of these lines, by line id; one document left out. */
    private Map<Long, BigDecimal> quantitiesTaken(Collection<PurchaseDocumentLine> sourceLines, Tracking tracking, Long excludedId) {
        Map<Long, BigDecimal> taken = new HashMap<>();
        List<Long> ids = sourceLines.stream().map(PurchaseDocumentLine::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return taken;
        }
        for (Object[] row : documentRepository.quantitiesTaken(currentUser.companyId(), ids, tracking.taker(),
                tracking.counting(), excludedId == null ? -1L : excludedId)) {
            taken.put((Long) row[0], (BigDecimal) row[1]);
        }
        return taken;
    }

    /** What is left of each line of a document, once what its tracked documents have taken is off. */
    private Function<PurchaseDocumentLine, BigDecimal> remainingOf(PurchaseDocument source) {
        Map<Long, BigDecimal> taken = quantitiesTaken(source.getLines(), trackingOf(source.getType()), null);
        return line -> line.getQuantity().subtract(taken.getOrDefault(line.getId(), BigDecimal.ZERO));
    }

    private static List<PurchaseDocumentLine> sourceLinesOf(PurchaseDocument document) {
        return document.getLines().stream().map(PurchaseDocumentLine::getSourceLine).filter(Objects::nonNull).toList();
    }

    /**
     * A goods receipt cannot receive more of a line than the order has left of it, nor a return note return more than
     * was received or invoiced. Checked when the draft is validated - the moment its goods move - so that two drafts
     * made for the same remainder cannot both go through.
     */
    private void checkAgainstSource(PurchaseDocument document) {
        PurchaseDocument source = document.getSource();
        Tracking tracking = source == null ? null : trackingOf(source.getType());
        if (tracking == null || tracking.taker() != document.getType()) {
            return;
        }

        // Serialize with any other draft of the same source: its quantities are read below and must still be true when we write
        documentRepository.lockById(source.getId(), currentUser.companyId());
        Map<Long, BigDecimal> taken = quantitiesTaken(sourceLinesOf(document), tracking, document.getId());
        Map<Long, BigDecimal> asked = new HashMap<>();
        for (PurchaseDocumentLine line : document.getLines()) {
            if (line.getSourceLine() != null) {
                asked.merge(line.getSourceLine().getId(), line.getQuantity(), BigDecimal::add);
            }
        }
        for (PurchaseDocumentLine line : document.getLines()) {
            PurchaseDocumentLine sourceLine = line.getSourceLine();
            if (sourceLine == null) {
                continue;
            }
            BigDecimal left = sourceLine.getQuantity().subtract(taken.getOrDefault(sourceLine.getId(), BigDecimal.ZERO));
            if (asked.get(sourceLine.getId()).compareTo(left) > 0) {
                throw new BusinessRuleException(left.signum() <= 0
                        ? "Nothing is left to " + tracking.verb() + " of \"" + line.getDesignation() + "\""
                        : "Only " + left.stripTrailingZeros().toPlainString() + " of \"" + line.getDesignation()
                        + "\" left to " + tracking.verb());
            }
        }
    }

    /** Puts on each line of a saved document what has been taken of it, and what a draft may still take of its source. */
    private void addQuantities(PurchaseDocument document, PurchaseDocumentResponse response) {
        if (document.getId() == null) {
            return;
        }
        List<PurchaseDocumentLine> lines = document.getLines();

        Tracking own = trackingOf(document.getType());
        if (own != null) {
            Map<Long, BigDecimal> taken = quantitiesTaken(lines, own, null);
            for (int i = 0; i < lines.size(); i++) {
                BigDecimal done = taken.getOrDefault(lines.get(i).getId(), BigDecimal.ZERO);
                response.getLines().get(i).setFulfilledQuantity(done);
                response.getLines().get(i).setRemainingQuantity(lines.get(i).getQuantity().subtract(done));
            }
        }

        PurchaseDocument source = document.getSource();
        Tracking sourceTracking = source == null ? null : trackingOf(source.getType());
        if (sourceTracking != null && sourceTracking.taker() == document.getType()) {
            Map<Long, BigDecimal> taken = quantitiesTaken(sourceLinesOf(document), sourceTracking, document.getId());
            for (int i = 0; i < lines.size(); i++) {
                PurchaseDocumentLine sourceLine = lines.get(i).getSourceLine();
                if (sourceLine != null) {
                    response.getLines().get(i).setSourceRemaining(
                            sourceLine.getQuantity().subtract(taken.getOrDefault(sourceLine.getId(), BigDecimal.ZERO)));
                }
            }
        }
    }

    // ----- Dashboard -----

    /** An invoice counts as a purchase from the day it is dated, unless it was cancelled. */
    private static final List<PurchaseDocumentStatus> INVOICED = List.of(
            PurchaseDocumentStatus.VALIDATED, PurchaseDocumentStatus.PARTIALLY_PAID, PurchaseDocumentStatus.PAID);
    /** An invoice that still has something to pay. */
    private static final List<PurchaseDocumentStatus> UNPAID = List.of(
            PurchaseDocumentStatus.VALIDATED, PurchaseDocumentStatus.PARTIALLY_PAID);

    /**
     * The purchase figures of the dashboard as of {@code today}. Purchases are net: the invoices of the period minus
     * its supplier credit notes. The month is the calendar month; the series is the last six, oldest first.
     */
    @Transactional(readOnly = true)
    public PurchaseFigures figures(LocalDate today) {
        Long companyId = currentUser.companyId();
        List<MonthAmount> months = new ArrayList<>();
        for (int back = 5; back >= 0; back--) {
            YearMonth month = YearMonth.from(today).minusMonths(back);
            months.add(new MonthAmount(month.toString(), netPurchases(month.atDay(1), month.atEndOfMonth())));
        }
        List<PurchaseDocumentSummaryResponse> late = documentRepository
                .overdueInvoices(companyId, PurchaseDocumentType.PURCHASE_INVOICE, UNPAID, today, PageRequest.of(0, 5))
                .stream().map(documentMapper::toSummary).toList();

        return new PurchaseFigures(
                netPurchases(today, today),
                months.get(months.size() - 1).amount(),
                documentRepository.unpaid(companyId, PurchaseDocumentType.PURCHASE_INVOICE, UNPAID),
                documentRepository.overdue(companyId, PurchaseDocumentType.PURCHASE_INVOICE, UNPAID, today),
                late,
                months);
    }

    private BigDecimal netPurchases(LocalDate from, LocalDate to) {
        Long companyId = currentUser.companyId();
        return documentRepository.sumTotal(companyId, PurchaseDocumentType.PURCHASE_INVOICE, INVOICED, from, to)
                .subtract(documentRepository.sumTotal(companyId, PurchaseDocumentType.PURCHASE_CREDIT_NOTE,
                        List.of(PurchaseDocumentStatus.VALIDATED), from, to));
    }

    // ----- helpers -----

    private PurchaseDocument getDocument(Long id) {
        return documentRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Purchase document", id));
    }

    private void requireDraft(PurchaseDocument document, String action) {
        if (!document.isDraft()) {
            throw new BusinessRuleException("Only a draft can be " + action
                    + " — this document is " + document.getStatus().name().toLowerCase());
        }
    }

    /** Only an invoice that stands - validated, partly paid or paid - can be credited. */
    private static void requireCreditable(PurchaseDocument invoice) {
        if (invoice.isDraft() || invoice.getStatus() == PurchaseDocumentStatus.CANCELLED) {
            throw new BusinessRuleException("Only a validated invoice can be credited");
        }
    }

    /** The sum of the validated credit notes of an invoice - the flush makes the one just validated or cancelled count. */
    private BigDecimal creditedOn(PurchaseDocument invoice) {
        documentRepository.flush();
        return documentRepository.sumByInvoice(currentUser.companyId(), invoice.getId(),
                PurchaseDocumentType.PURCHASE_CREDIT_NOTE, PurchaseDocumentStatus.VALIDATED);
    }

    /**
     * A credit note that has just been validated takes its total off its invoice - which cannot be credited for
     * more than it is worth. Refused, the credit note stays a draft and its number is given back.
     */
    private void applyToInvoice(PurchaseDocument note) {
        PurchaseDocument invoice = note.getSource();
        requireCreditable(invoice);
        BigDecimal credited = creditedOn(invoice);
        if (credited.compareTo(invoice.getTotal()) > 0) {
            BigDecimal creditable = invoice.getTotal().subtract(credited.subtract(note.getTotal()));
            throw new BusinessRuleException("This credit note is more than the " + creditable.toPlainString()
                    + " that can still be credited on " + invoice.getReference());
        }
        invoice.applyCredited(credited);
    }

    /** Whether a document made from this one, of that type, is still standing. */
    private boolean hasLiveChild(PurchaseDocument parent, PurchaseDocumentType childType) {
        return documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(parent.getId(), currentUser.companyId())
                .stream().anyMatch(child -> child.getType() == childType
                        && child.getStatus() != PurchaseDocumentStatus.CANCELLED);
    }

    private void requireNotInvoiced(PurchaseDocument source) {
        if (hasLiveChild(source, PurchaseDocumentType.PURCHASE_INVOICE)) {
            throw new BusinessRuleException("This " + source.getType().getLabel().toLowerCase() + " is already invoiced");
        }
    }

    /** The documents an invoice can be made from: an order and a goods receipt. */
    private static boolean isInvoiceable(PurchaseDocument document) {
        return document.getType() == PurchaseDocumentType.PURCHASE_ORDER
                || document.getType() == PurchaseDocumentType.GOODS_RECEIPT;
    }

    /**
     * Whether the document is what brings its goods into the stock: a goods receipt always is; an invoice is
     * unless it was made from a goods receipt - that receipt brought them in already.
     */
    private static boolean receivesGoods(PurchaseDocument document) {
        return document.getType() == PurchaseDocumentType.GOODS_RECEIPT
                || (document.getType() == PurchaseDocumentType.PURCHASE_INVOICE
                && (document.getSource() == null
                || document.getSource().getType() != PurchaseDocumentType.GOODS_RECEIPT));
    }

    private void checkDates(PurchaseDocumentRequest request) {
        if (request.getDueDate() != null && request.getDueDate().isBefore(request.getIssueDate())) {
            throw new BusinessRuleException("The due date cannot be before the issue date");
        }
    }

    /** A goods receipt and an invoice say where their goods go; an order has no warehouse. */
    private Warehouse resolveWarehouse(PurchaseDocumentRequest request) {
        if (!request.getType().hasWarehouse()) {
            return null;
        }
        if (request.getWarehouseId() == null) {
            throw new BusinessRuleException("A " + request.getType().getLabel().toLowerCase() + " needs a warehouse");
        }
        return warehouseService.getAssignable(request.getWarehouseId());
    }

    /** Products and taxes the request points at: fetched once, checked to be usable. */
    private References resolveReferences(PurchaseDocumentRequest request) {
        Set<Long> productIds = new HashSet<>();
        Set<Long> taxIds = new HashSet<>();
        if (request.getTaxIds() != null) {
            taxIds.addAll(request.getTaxIds());
        }
        for (PurchaseDocumentLineRequest line : request.getLines()) {
            if (line.getProductId() != null) {
                productIds.add(line.getProductId());
            }
            if (line.getVatTaxId() != null) {
                taxIds.add(line.getVatTaxId());
            }
        }

        Map<Long, Product> products = productService.resolvePurchasable(productIds);
        Map<Long, Tax> taxes = taxService.resolveAssignable(taxIds).stream()
                .collect(Collectors.toMap(Tax::getId, Function.identity()));

        if (request.getTaxIds() != null) {
            for (Long taxId : request.getTaxIds()) {
                Tax tax = taxes.get(taxId);
                requireActive(tax);
                if (tax.getKind() == TaxKind.VAT_RATE) {
                    throw new BusinessRuleException("\"" + tax.getName()
                            + "\" is a VAT rate: pick it on the lines, not on the whole document");
                }
            }
        }
        for (PurchaseDocumentLineRequest line : request.getLines()) {
            if (line.getVatTaxId() != null) {
                Tax tax = taxes.get(line.getVatTaxId());
                requireActive(tax);
                if (tax.getKind() != TaxKind.VAT_RATE) {
                    throw new BusinessRuleException("\"" + tax.getName() + "\" is not a VAT rate");
                }
            }
        }
        return new References(products, taxes);
    }

    private static void requireActive(Tax tax) {
        if (!tax.isActive()) {
            throw new BusinessRuleException("The tax \"" + tax.getName() + "\" is not active");
        }
    }

    /** Replaces the lines and the document taxes with what the request says, then recomputes. */
    private void fill(PurchaseDocument document, PurchaseDocumentRequest request, References references) {
        // A draft made from a document keeps following that document's lines; anywhere else the field is ignored
        Map<Long, PurchaseDocumentLine> sourceLines = new HashMap<>();
        if (document.getSource() != null) {
            document.getSource().getLines().forEach(line -> sourceLines.put(line.getId(), line));
        }
        document.getLines().clear();
        for (PurchaseDocumentLineRequest lineRequest : request.getLines()) {
            PurchaseDocumentLine line = toLine(lineRequest, references);
            if (lineRequest.getSourceLineId() != null && document.getSource() != null) {
                line.setSourceLine(sourceLines.get(lineRequest.getSourceLineId()));
                if (line.getSourceLine() == null) {
                    throw new BusinessRuleException("A line does not belong to the document this one was made from");
                }
            }
            document.addLine(line);
        }

        Set<Long> taxIds = request.getTaxIds() == null ? Set.of() : request.getTaxIds();
        document.selectTaxes(taxIds.stream().map(references.taxes()::get).toList());
        document.recalculate();
    }

    private PurchaseDocumentLine toLine(PurchaseDocumentLineRequest request, References references) {
        Product product = request.getProductId() == null ? null : references.products().get(request.getProductId());

        String designation = trimToNull(request.getDesignation());
        if (designation == null) {
            if (product == null) {
                throw new BusinessRuleException("A line without a product needs a designation");
            }
            designation = product.getName();
        }

        PurchaseDocumentLine line = new PurchaseDocumentLine();
        line.setProduct(product);
        line.setDesignation(designation);
        line.setReference(trimToNull(request.getReference()) != null
                ? request.getReference().trim()
                : product != null ? product.getReference() : null);
        line.setQuantity(request.getQuantity());
        line.setUnitPrice(request.getUnitPrice() != null ? request.getUnitPrice()
                : product != null && product.getPurchasePrice() != null ? product.getPurchasePrice() : BigDecimal.ZERO);
        line.setDiscountRate(request.getDiscountRate() != null ? request.getDiscountRate() : BigDecimal.ZERO);

        Tax vat = request.getVatTaxId() == null ? null : references.taxes().get(request.getVatTaxId());
        line.setVatTaxId(vat == null ? null : vat.getId());
        line.setVatRate(vat == null ? BigDecimal.ZERO : vat.getRate());
        return line;
    }

    /** The goods on the lines — a service or a free line has no stock to receive. */
    private static List<StockRequirement> goodsOf(PurchaseDocument document) {
        return document.getLines().stream()
                .filter(line -> line.getProduct() != null && line.getProduct().getKind() == ProductKind.GOOD)
                .map(line -> new StockRequirement(line.getProduct(), line.getQuantity()))
                .toList();
    }

    /**
     * The next number from the document type's numbering settings. The sequence row is locked,
     * so two concurrent validations never get the same number; one already taken (the counter
     * was moved by hand) is skipped.
     */
    private String nextFreeReference(PurchaseDocumentType type) {
        String candidate;
        do {
            candidate = numberingService.allocate(type.getNumbering());
        } while (documentRepository.existsByCompanyIdAndTypeAndReferenceIgnoreCase(
                currentUser.companyId(), type, candidate));
        return candidate;
    }

    private PurchaseDocumentResponse toResponse(PurchaseDocument document) {
        PurchaseDocumentResponse response = documentMapper.toResponse(document);
        addQuantities(document, response);
        if (document.getId() != null) {
            documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(document.getId(), currentUser.companyId())
                    .stream()
                    .map(documentMapper::toSummary)
                    .forEach(response.getDerived()::add);
        }
        return response;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record References(Map<Long, Product> products, Map<Long, Tax> taxes) {
    }
}
