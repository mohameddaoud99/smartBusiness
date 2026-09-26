package com.sales.smartBusiness.sales;

import com.sales.smartBusiness.common.MonthAmount;
import com.sales.smartBusiness.common.SearchPattern;
import com.sales.smartBusiness.company.CompanyService;
import com.sales.smartBusiness.customer.Customer;
import com.sales.smartBusiness.customer.CustomerService;
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

@Service
@RequiredArgsConstructor
@Transactional
public class SalesDocumentService {

    private final SalesDocumentRepository documentRepository;
    private final CompanyService companyService;
    private final CustomerService customerService;
    private final ProductService productService;
    private final TaxService taxService;
    private final NumberingService numberingService;
    private final StockService stockService;
    private final WarehouseService warehouseService;
    private final SalesDocumentMapper documentMapper;
    private final CurrentUser currentUser;

    @Transactional(readOnly = true)
    public Page<SalesDocumentSummaryResponse> search(SalesDocumentType type, String search,
                                                     SalesDocumentStatus status, Long customerId,
                                                     Pageable pageable) {
        return documentRepository.search(currentUser.companyId(), type, SearchPattern.like(search),
                        status, customerId, pageable)
                .map(documentMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public SalesDocumentResponse findById(Long id) {
        return toResponse(getDocument(id));
    }

    public SalesDocumentResponse create(SalesDocumentRequest request) {
        if (request.getType() == SalesDocumentType.CREDIT_NOTE) {
            throw new BusinessRuleException("A credit note is made from an invoice: open the invoice and create it from there");
        }
        checkDates(request);
        Customer customer = customerService.getAssignable(request.getCustomerId());
        Warehouse warehouse = resolveWarehouse(request);
        References references = resolveReferences(request);

        SalesDocument document = documentMapper.toEntity(request);
        document.setCompany(companyService.currentReference());
        document.setCustomer(customer);
        document.setWarehouse(warehouse);
        fill(document, request, references);

        return toResponse(documentRepository.save(document));
    }

    public SalesDocumentResponse update(Long id, SalesDocumentRequest request) {
        SalesDocument document = getDocument(id);
        requireDraft(document, "edited");
        if (request.getType() != document.getType()) {
            throw new BusinessRuleException("A document's type cannot be changed");
        }
        if (document.getType() == SalesDocumentType.CREDIT_NOTE
                && !document.getCustomer().getId().equals(request.getCustomerId())) {
            throw new BusinessRuleException("A credit note stays with the customer of its invoice");
        }
        if (document.getType() == SalesDocumentType.RETURN_NOTE && document.getSource() != null
                && !document.getCustomer().getId().equals(request.getCustomerId())) {
            throw new BusinessRuleException("A return note stays with the customer of the document it was made from");
        }
        checkDates(request);
        Customer customer = customerService.getAssignable(request.getCustomerId());
        Warehouse warehouse = resolveWarehouse(request);
        References references = resolveReferences(request);

        documentMapper.updateEntity(request, document);
        document.setCustomer(customer);
        document.setWarehouse(warehouse);
        fill(document, request, references);

        return toResponse(document);
    }

    /** Only a draft can go: an issued document is cancelled, never erased. */
    public void delete(Long id) {
        SalesDocument document = getDocument(id);
        requireDraft(document, "deleted");
        documentRepository.delete(document);
    }

    /**
     * Freezes a draft: the number is allocated now — not when the draft was started — so
     * a discarded draft leaves no gap in the sequence.
     */
    public SalesDocumentResponse issue(Long id) {
        SalesDocument document = getDocument(id);
        requireDraft(document, "issued");
        checkAgainstSource(document);

        document.setReference(nextFreeReference(document.getType()));
        document.setStatus(SalesDocumentStatus.ISSUED);
        if (takesStockOut(document)) {
            // Issuing the invoice is the sale: the goods leave, out of the order reservation if it came from one.
            // Refused - and the invoice stays a draft, its number given back - when a strict product lacks them.
            stockService.deliver(StockSource.SALES_DOCUMENT, document.getId(), document.getWarehouse(),
                    goodsOf(document), orderOf(document));
        }
        if (document.getType() == SalesDocumentType.RETURN_NOTE) {
            // The goods come back into the warehouse: nothing to refuse
            stockService.receive(StockSource.SALES_DOCUMENT, document.getId(), document.getWarehouse(),
                    goodsOf(document));
        }
        if (document.getType() == SalesDocumentType.CREDIT_NOTE) {
            applyToInvoice(document);
        }
        return toResponse(document);
    }

    /** Accept or reject a quote, confirm a sales order. Cancelling has its own operation (own permission). */
    public SalesDocumentResponse changeStatus(Long id, SalesDocumentStatus target) {
        SalesDocument document = getDocument(id);
        if (document.isDraft()) {
            throw new BusinessRuleException("Issue the draft first");
        }
        if (document.getType() == SalesDocumentType.INVOICE) {
            throw new BusinessRuleException(
                    "An invoice is paid by its payments: record or cancel a payment instead of changing its status");
        }
        if (target == SalesDocumentStatus.CANCELLED) {
            throw new BusinessRuleException("Use the cancel action to cancel a document");
        }
        if (!document.canMoveTo(target)) {
            throw new BusinessRuleException(sentence(document.getType().withArticle())
                    + " that is " + label(document.getStatus()) + " cannot become " + label(target));
        }

        document.setStatus(target);
        if (target == SalesDocumentStatus.CONFIRMED) {
            // A confirmed order promises its goods: they stop being available to anyone else
            stockService.reserve(StockSource.SALES_DOCUMENT, document.getId(), goodsOf(document));
        }
        if (target == SalesDocumentStatus.DELIVERED) {
            // The goods leave: taken out of the stock, and out of the order's reservation if it came from one.
            // Refused — and the note stays "created" — when a strict product lacks the goods.
            stockService.deliver(StockSource.SALES_DOCUMENT, document.getId(), document.getWarehouse(),
                    goodsOf(document), orderOf(document));
        }
        return toResponse(document);
    }

    public SalesDocumentResponse cancel(Long id) {
        SalesDocument document = getDocument(id);
        if (document.getType() == SalesDocumentType.QUOTE) {
            throw new BusinessRuleException("A quote cannot be cancelled — mark it as rejected instead");
        }
        if (document.isDraft()) {
            throw new BusinessRuleException("A draft is deleted, not cancelled");
        }
        if (document.getType() == SalesDocumentType.INVOICE && document.getPaidAmount().signum() > 0) {
            throw new BusinessRuleException("This invoice has payments. Cancel them before cancelling the invoice.");
        }
        if (document.getType() == SalesDocumentType.INVOICE && hasLiveChild(document, SalesDocumentType.CREDIT_NOTE)) {
            throw new BusinessRuleException("This invoice has credit notes. Cancel them before cancelling the invoice.");
        }
        if ((document.getType() == SalesDocumentType.DELIVERY_NOTE || document.getType() == SalesDocumentType.INVOICE)
                && hasLiveChild(document, SalesDocumentType.RETURN_NOTE)) {
            // Cancelling it would bring the goods back into the stock a second time
            throw new BusinessRuleException("This " + document.getType().getLabel().toLowerCase()
                    + " has a return note. Cancel the return note before cancelling it.");
        }
        if (!document.canMoveTo(SalesDocumentStatus.CANCELLED)) {
            throw new BusinessRuleException(sentence(document.getType().withArticle())
                    + " that is " + label(document.getStatus()) + " cannot be cancelled");
        }
        if (document.getType() == SalesDocumentType.SALES_ORDER && hasLiveChild(document, SalesDocumentType.DELIVERY_NOTE)) {
            throw new BusinessRuleException(
                    "This sales order has delivery notes. Cancel them before cancelling the order.");
        }
        if (isInvoiceable(document) && hasLiveChild(document, SalesDocumentType.INVOICE)) {
            throw new BusinessRuleException("This " + document.getType().getLabel().toLowerCase()
                    + " has an invoice. Cancel the invoice before cancelling it.");
        }

        boolean stockLeft = document.getStatus() == SalesDocumentStatus.DELIVERED || takesStockOut(document);
        document.setStatus(SalesDocumentStatus.CANCELLED);
        if (document.getType() == SalesDocumentType.SALES_ORDER) {
            // Whatever the order still holds goes back on sale (nothing if it was never confirmed)
            stockService.release(StockSource.SALES_DOCUMENT, document.getId());
        } else if (stockLeft) {
            // The goods come back, and the order they left under - if it still stands - holds them again
            SalesDocument order = document.getSource();
            boolean orderStands = order != null && order.getType() == SalesDocumentType.SALES_ORDER
                    && order.getStatus() == SalesDocumentStatus.CONFIRMED;
            stockService.undoDelivery(StockSource.SALES_DOCUMENT, document.getId(), orderStands);
        }
        if (document.getType() == SalesDocumentType.RETURN_NOTE) {
            // The goods that came back go out again - refused, and the note stays issued, when a strict product
            // no longer has them (they were sold or reserved since)
            stockService.reverseReceipt(StockSource.SALES_DOCUMENT, document.getId());
        }
        if (document.getType() == SalesDocumentType.CREDIT_NOTE) {
            // The invoice is owed what this credit note took off it
            SalesDocument invoice = document.getSource();
            invoice.applyCredited(creditedOn(invoice));
        }
        return toResponse(document);
    }

    /**
     * Makes a draft delivery note from a CONFIRMED sales order — the goods it promised — copying its
     * lines and taxes as they are. The note is a draft the user adjusts to what really leaves: a partial
     * delivery is a note with lower quantities, and an order can have several.
     */
    public SalesDocumentResponse convertToDeliveryNote(Long id) {
        SalesDocument order = getDocument(id);
        if (order.getType() != SalesDocumentType.SALES_ORDER) {
            throw new BusinessRuleException("Only a sales order can be turned into a delivery note");
        }
        if (order.getStatus() != SalesDocumentStatus.CONFIRMED) {
            throw new BusinessRuleException("Confirm the sales order first: a delivery note delivers what it reserved");
        }
        if (hasLiveChild(order, SalesDocumentType.INVOICE)) {
            throw new BusinessRuleException("This sales order is already invoiced: its goods have left with the invoice");
        }

        SalesDocument note = new SalesDocument();
        note.setCompany(order.getCompany());
        note.setType(SalesDocumentType.DELIVERY_NOTE);
        note.setCustomer(order.getCustomer());
        note.setWarehouse(warehouseService.getDefault());
        note.setSource(order);
        note.setIssueDate(LocalDate.now());
        note.copyContentFrom(order, remainingOf(order));
        if (note.getLines().isEmpty()) {
            throw new BusinessRuleException("Everything ordered is already on a delivery note");
        }

        return toResponse(documentRepository.save(note));
    }

    /**
     * Makes a draft return note from a DELIVERED delivery note or an issued invoice - paid or not - copying its lines
     * and taxes: the user lowers the quantities to what really comes back. Issuing it puts those goods back into
     * the stock. Several return notes can be made from one document (a return in parts); what has been returned is
     * not tracked line by line yet, so nothing stops returning more than was delivered.
     */
    public SalesDocumentResponse convertToReturnNote(Long id) {
        SalesDocument source = getDocument(id);
        switch (source.getType()) {
            case DELIVERY_NOTE -> {
                if (source.getStatus() != SalesDocumentStatus.DELIVERED) {
                    throw new BusinessRuleException("Only a delivered delivery note can be returned");
                }
            }
            case INVOICE -> {
                if (source.isDraft() || source.getStatus() == SalesDocumentStatus.CANCELLED) {
                    throw new BusinessRuleException("Only an issued invoice can be returned");
                }
            }
            default -> throw new BusinessRuleException("Only a delivery note or an invoice can be returned");
        }

        SalesDocument note = new SalesDocument();
        note.setCompany(source.getCompany());
        note.setType(SalesDocumentType.RETURN_NOTE);
        note.setCustomer(source.getCustomer());
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
     * Makes a draft credit note from an issued invoice — paid or not — copying its lines and taxes: the user
     * lowers the quantities to what is really credited. Several credit notes can be made on one invoice, up to
     * its total. A credit note is a financial document: it moves no stock (goods coming back are entered in the
     * stock by hand for now).
     */
    public SalesDocumentResponse convertToCreditNote(Long id) {
        SalesDocument invoice = getDocument(id);
        if (invoice.getType() != SalesDocumentType.INVOICE) {
            throw new BusinessRuleException("Only an invoice can be credited");
        }
        requireCreditable(invoice);
        if (invoice.getCreditedAmount().compareTo(invoice.getTotal()) >= 0) {
            throw new BusinessRuleException("This invoice is already credited in full");
        }

        SalesDocument note = new SalesDocument();
        note.setCompany(invoice.getCompany());
        note.setType(SalesDocumentType.CREDIT_NOTE);
        note.setCustomer(invoice.getCustomer());
        note.setSource(invoice);
        note.setIssueDate(LocalDate.now());
        note.copyContentFrom(invoice);

        return toResponse(documentRepository.save(note));
    }

    /**
     * Makes a draft invoice from an issued or accepted quote, a CONFIRMED sales order, or a DELIVERED delivery
     * note, copying its lines and taxes as they are. One live invoice per source. An order whose goods went
     * out on delivery notes is invoiced through them, not directly. What the invoice does to the stock is
     * decided when it is issued: nothing when it comes from a delivery note (the goods already left), the
     * goods leave otherwise.
     */
    public SalesDocumentResponse convertToInvoice(Long id) {
        SalesDocument source = getDocument(id);
        switch (source.getType()) {
            case QUOTE -> {
                if (source.getStatus() != SalesDocumentStatus.ISSUED && source.getStatus() != SalesDocumentStatus.ACCEPTED) {
                    throw new BusinessRuleException("Only an issued or accepted quote can be invoiced");
                }
                if (documentRepository.existsBySourceIdAndCompanyIdAndStatusNot(
                        id, currentUser.companyId(), SalesDocumentStatus.CANCELLED)) {
                    throw new BusinessRuleException("This quote already has a sales order or an invoice");
                }
            }
            case SALES_ORDER -> {
                if (source.getStatus() != SalesDocumentStatus.CONFIRMED) {
                    throw new BusinessRuleException("Confirm the sales order first: an invoice sells what it reserved");
                }
                if (hasLiveChild(source, SalesDocumentType.DELIVERY_NOTE)) {
                    throw new BusinessRuleException("This sales order has delivery notes: invoice them instead");
                }
                requireNotInvoiced(source);
            }
            case DELIVERY_NOTE -> {
                if (source.getStatus() != SalesDocumentStatus.DELIVERED) {
                    throw new BusinessRuleException("Only a delivered delivery note can be invoiced");
                }
                requireNotInvoiced(source);
            }
            default -> throw new BusinessRuleException(sentence(source.getType().withArticle())
                    + " cannot be turned into an invoice");
        }

        SalesDocument invoice = new SalesDocument();
        invoice.setCompany(source.getCompany());
        invoice.setType(SalesDocumentType.INVOICE);
        invoice.setCustomer(source.getCustomer());
        invoice.setWarehouse(source.getType() == SalesDocumentType.DELIVERY_NOTE
                ? source.getWarehouse() : warehouseService.getDefault());
        invoice.setSource(source);
        invoice.setIssueDate(LocalDate.now());
        invoice.copyContentFrom(source);

        return toResponse(documentRepository.save(invoice));
    }

    /**
     * Records how much of an invoice has been paid. The caller passes the SUM of its active payments,
     * never "one more", so the invoice cannot drift from them. Moves the status: unpaid, partly paid, paid.
     */
    public void applyPaid(Long invoiceId, BigDecimal paid) {
        getDocument(invoiceId).applyPaid(paid);
    }

    /** The invoice a payment is about - the one place that says which invoices can still be paid. */
    @Transactional(readOnly = true)
    public SalesDocument getPayableInvoice(Long id) {
        SalesDocument invoice = getDocument(id);
        if (invoice.getType() != SalesDocumentType.INVOICE) {
            throw new BusinessRuleException("Only an invoice can be paid");
        }
        if (invoice.isDraft()) {
            throw new BusinessRuleException("Issue the invoice before recording a payment");
        }
        if (invoice.getStatus() == SalesDocumentStatus.CANCELLED) {
            throw new BusinessRuleException("This invoice is cancelled");
        }
        if (invoice.getStatus() == SalesDocumentStatus.PAID) {
            throw new BusinessRuleException("This invoice is already paid in full");
        }
        return invoice;
    }

    /**
     * Makes a draft sales order from a quote, copying its lines and taxes as they are — the
     * price the customer was quoted is the price they get. One order per quote, unless that
     * order was cancelled.
     */
    public SalesDocumentResponse convertToOrder(Long id) {
        SalesDocument quote = getDocument(id);
        if (quote.getType() != SalesDocumentType.QUOTE) {
            throw new BusinessRuleException("Only a quote can be turned into a sales order");
        }
        if (quote.getStatus() != SalesDocumentStatus.ISSUED && quote.getStatus() != SalesDocumentStatus.ACCEPTED) {
            throw new BusinessRuleException("Only an issued or accepted quote can be turned into a sales order");
        }
        if (documentRepository.existsBySourceIdAndCompanyIdAndStatusNot(
                id, currentUser.companyId(), SalesDocumentStatus.CANCELLED)) {
            throw new BusinessRuleException("This quote already has a sales order or an invoice");
        }

        SalesDocument order = new SalesDocument();
        order.setCompany(quote.getCompany());
        order.setType(SalesDocumentType.SALES_ORDER);
        order.setCustomer(quote.getCustomer());
        order.setSource(quote);
        order.setIssueDate(LocalDate.now());
        order.copyContentFrom(quote);

        return toResponse(documentRepository.save(order));
    }

    /**
     * The totals a request would produce, without saving anything — so the editor shows
     * them live and the calculation is written once, here, not again in the browser.
     */
    @Transactional(readOnly = true)
    public SalesDocumentResponse preview(SalesDocumentRequest request) {
        checkDates(request);
        References references = resolveReferences(request);

        SalesDocument document = documentMapper.toEntity(request);
        fill(document, request, references);
        return documentMapper.toResponse(document);
    }

    // ----- Quantities followed line by line -----

    /** A document that takes quantities off the lines of another: which type, which of its statuses count, what it does. */
    private record Tracking(SalesDocumentType taker, List<SalesDocumentStatus> counting, String verb) {
    }

    /**
     * What is followed line by line: an order is delivered on delivery notes (issued or delivered ones count: the
     * goods are spoken for); a delivery note or an invoice is returned on return notes (issued ones count).
     */
    private static Tracking trackingOf(SalesDocumentType source) {
        return switch (source) {
            case SALES_ORDER -> new Tracking(SalesDocumentType.DELIVERY_NOTE,
                    List.of(SalesDocumentStatus.ISSUED, SalesDocumentStatus.DELIVERED), "deliver");
            case DELIVERY_NOTE, INVOICE -> new Tracking(SalesDocumentType.RETURN_NOTE,
                    List.of(SalesDocumentStatus.ISSUED), "return");
            default -> null;
        };
    }

    /** What the documents of the tracked type have taken of each of these lines, by line id; one document left out. */
    private Map<Long, BigDecimal> quantitiesTaken(Collection<SalesDocumentLine> sourceLines, Tracking tracking, Long excludedId) {
        Map<Long, BigDecimal> taken = new HashMap<>();
        List<Long> ids = sourceLines.stream().map(SalesDocumentLine::getId).filter(Objects::nonNull).toList();
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
    private Function<SalesDocumentLine, BigDecimal> remainingOf(SalesDocument source) {
        Map<Long, BigDecimal> taken = quantitiesTaken(source.getLines(), trackingOf(source.getType()), null);
        return line -> line.getQuantity().subtract(taken.getOrDefault(line.getId(), BigDecimal.ZERO));
    }

    private static List<SalesDocumentLine> sourceLinesOf(SalesDocument document) {
        return document.getLines().stream().map(SalesDocumentLine::getSourceLine).filter(Objects::nonNull).toList();
    }

    /**
     * A delivery note cannot deliver more of a line than the order has left of it, nor a return note return more than
     * was delivered or invoiced. Checked when the draft is issued - the moment its goods are spoken for - so that
     * two drafts made for the same remainder cannot both go through.
     */
    private void checkAgainstSource(SalesDocument document) {
        SalesDocument source = document.getSource();
        Tracking tracking = source == null ? null : trackingOf(source.getType());
        if (tracking == null || tracking.taker() != document.getType()) {
            return;
        }

        // Serialize with any other draft of the same source: its quantities are read below and must still be true when we write
        documentRepository.lockById(source.getId(), currentUser.companyId());
        Map<Long, BigDecimal> taken = quantitiesTaken(sourceLinesOf(document), tracking, document.getId());
        Map<Long, BigDecimal> asked = new HashMap<>();
        for (SalesDocumentLine line : document.getLines()) {
            if (line.getSourceLine() != null) {
                asked.merge(line.getSourceLine().getId(), line.getQuantity(), BigDecimal::add);
            }
        }
        for (SalesDocumentLine line : document.getLines()) {
            SalesDocumentLine sourceLine = line.getSourceLine();
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
    private void addQuantities(SalesDocument document, SalesDocumentResponse response) {
        if (document.getId() == null) {
            return;
        }
        List<SalesDocumentLine> lines = document.getLines();

        Tracking own = trackingOf(document.getType());
        if (own != null) {
            Map<Long, BigDecimal> taken = quantitiesTaken(lines, own, null);
            for (int i = 0; i < lines.size(); i++) {
                BigDecimal done = taken.getOrDefault(lines.get(i).getId(), BigDecimal.ZERO);
                response.getLines().get(i).setFulfilledQuantity(done);
                response.getLines().get(i).setRemainingQuantity(lines.get(i).getQuantity().subtract(done));
            }
        }

        SalesDocument source = document.getSource();
        Tracking sourceTracking = source == null ? null : trackingOf(source.getType());
        if (sourceTracking != null && sourceTracking.taker() == document.getType()) {
            Map<Long, BigDecimal> taken = quantitiesTaken(sourceLinesOf(document), sourceTracking, document.getId());
            for (int i = 0; i < lines.size(); i++) {
                SalesDocumentLine sourceLine = lines.get(i).getSourceLine();
                if (sourceLine != null) {
                    response.getLines().get(i).setSourceRemaining(
                            sourceLine.getQuantity().subtract(taken.getOrDefault(sourceLine.getId(), BigDecimal.ZERO)));
                }
            }
        }
    }

    // ----- Dashboard -----

    /** An invoice counts as revenue from the day it is issued, unless it was cancelled. */
    private static final List<SalesDocumentStatus> INVOICED = List.of(
            SalesDocumentStatus.ISSUED, SalesDocumentStatus.PARTIALLY_PAID, SalesDocumentStatus.PAID);
    /** An invoice that still has something to pay. */
    private static final List<SalesDocumentStatus> UNPAID = List.of(
            SalesDocumentStatus.ISSUED, SalesDocumentStatus.PARTIALLY_PAID);

    /**
     * The sales figures of the dashboard as of {@code today}. Revenue is net: the invoices of the period minus its
     * credit notes. The month is the calendar month; the series is the last six, oldest first.
     */
    @Transactional(readOnly = true)
    public SalesFigures figures(LocalDate today) {
        Long companyId = currentUser.companyId();
        List<MonthAmount> months = new ArrayList<>();
        for (int back = 5; back >= 0; back--) {
            YearMonth month = YearMonth.from(today).minusMonths(back);
            months.add(new MonthAmount(month.toString(), netRevenue(month.atDay(1), month.atEndOfMonth())));
        }
        List<SalesDocumentSummaryResponse> late = documentRepository
                .overdueInvoices(companyId, SalesDocumentType.INVOICE, UNPAID, today, PageRequest.of(0, 5))
                .stream().map(documentMapper::toSummary).toList();

        return new SalesFigures(
                netRevenue(today, today),
                months.get(months.size() - 1).amount(),
                documentRepository.unpaid(companyId, SalesDocumentType.INVOICE, UNPAID),
                documentRepository.overdue(companyId, SalesDocumentType.INVOICE, UNPAID, today),
                late,
                months);
    }

    private BigDecimal netRevenue(LocalDate from, LocalDate to) {
        Long companyId = currentUser.companyId();
        return documentRepository.sumTotal(companyId, SalesDocumentType.INVOICE, INVOICED, from, to)
                .subtract(documentRepository.sumTotal(companyId, SalesDocumentType.CREDIT_NOTE,
                        List.of(SalesDocumentStatus.ISSUED), from, to));
    }

    // ----- helpers -----

    private SalesDocument getDocument(Long id) {
        return documentRepository.findByIdAndCompanyId(id, currentUser.companyId())
                .orElseThrow(() -> ResourceNotFoundException.of("Sales document", id));
    }

    /** A delivery note and an invoice say where their goods leave from; the other documents have no warehouse. */
    private Warehouse resolveWarehouse(SalesDocumentRequest request) {
        if (!request.getType().hasWarehouse()) {
            return null;
        }
        if (request.getWarehouseId() == null) {
            throw new BusinessRuleException(sentence(request.getType().withArticle()) + " needs a warehouse");
        }
        return warehouseService.getAssignable(request.getWarehouseId());
    }

    /** Only an invoice that stands — issued, partly paid or paid — can be credited. */
    private static void requireCreditable(SalesDocument invoice) {
        if (invoice.isDraft() || invoice.getStatus() == SalesDocumentStatus.CANCELLED) {
            throw new BusinessRuleException("Only an issued invoice can be credited");
        }
    }

    /** The sum of the issued credit notes of an invoice — the flush makes the one just issued or cancelled count. */
    private BigDecimal creditedOn(SalesDocument invoice) {
        documentRepository.flush();
        return documentRepository.sumByInvoice(currentUser.companyId(), invoice.getId(),
                SalesDocumentType.CREDIT_NOTE, SalesDocumentStatus.ISSUED);
    }

    /**
     * A credit note that has just been issued takes its total off its invoice — which cannot be credited for
     * more than it is worth. Refused, the credit note stays a draft and its number is given back.
     */
    private void applyToInvoice(SalesDocument note) {
        SalesDocument invoice = note.getSource();
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
    private boolean hasLiveChild(SalesDocument parent, SalesDocumentType childType) {
        return documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(parent.getId(), currentUser.companyId())
                .stream().anyMatch(child -> child.getType() == childType
                        && child.getStatus() != SalesDocumentStatus.CANCELLED);
    }

    private void requireNotInvoiced(SalesDocument source) {
        if (hasLiveChild(source, SalesDocumentType.INVOICE)) {
            throw new BusinessRuleException("This " + source.getType().getLabel().toLowerCase() + " is already invoiced");
        }
    }

    /** The documents an invoice can be made from through a chain: an order and a delivery note. */
    private static boolean isInvoiceable(SalesDocument document) {
        return document.getType() == SalesDocumentType.SALES_ORDER || document.getType() == SalesDocumentType.DELIVERY_NOTE;
    }

    /**
     * Whether an invoice is what takes its goods out of the stock: yes, unless it was made from a delivery
     * note - that note delivered them already.
     */
    private static boolean takesStockOut(SalesDocument document) {
        return document.getType() == SalesDocumentType.INVOICE
                && !document.isDraft()
                && (document.getSource() == null || document.getSource().getType() != SalesDocumentType.DELIVERY_NOTE);
    }

    /** The order a delivery note or an invoice came from, whose reservation it consumes - null for one made by hand. */
    private static Long orderOf(SalesDocument document) {
        SalesDocument source = document.getSource();
        return source != null && source.getType() == SalesDocumentType.SALES_ORDER ? source.getId() : null;
    }

    private void requireDraft(SalesDocument document, String action) {
        if (!document.isDraft()) {
            throw new BusinessRuleException("Only a draft can be " + action
                    + " — this document is " + label(document.getStatus()));
        }
    }

    private void checkDates(SalesDocumentRequest request) {
        if (request.getDueDate() != null && request.getDueDate().isBefore(request.getIssueDate())) {
            throw new BusinessRuleException("The due date cannot be before the issue date");
        }
    }

    /** Products and taxes the request points at: fetched once, checked to be usable. */
    private References resolveReferences(SalesDocumentRequest request) {
        Set<Long> productIds = new HashSet<>();
        Set<Long> taxIds = new HashSet<>();
        if (request.getTaxIds() != null) {
            taxIds.addAll(request.getTaxIds());
        }
        for (SalesDocumentLineRequest line : request.getLines()) {
            if (line.getProductId() != null) {
                productIds.add(line.getProductId());
            }
            if (line.getVatTaxId() != null) {
                taxIds.add(line.getVatTaxId());
            }
        }

        Map<Long, Product> products = productService.resolveSellable(productIds);
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
        for (SalesDocumentLineRequest line : request.getLines()) {
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
    private void fill(SalesDocument document, SalesDocumentRequest request, References references) {
        // A draft made from a document keeps following that document's lines; anywhere else the field is ignored
        Map<Long, SalesDocumentLine> sourceLines = new HashMap<>();
        if (document.getSource() != null) {
            document.getSource().getLines().forEach(line -> sourceLines.put(line.getId(), line));
        }
        document.getLines().clear();
        for (SalesDocumentLineRequest lineRequest : request.getLines()) {
            SalesDocumentLine line = toLine(lineRequest, references);
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

    private SalesDocumentLine toLine(SalesDocumentLineRequest request, References references) {
        Product product = request.getProductId() == null ? null : references.products().get(request.getProductId());

        String designation = trimToNull(request.getDesignation());
        if (designation == null) {
            if (product == null) {
                throw new BusinessRuleException("A line without a product needs a designation");
            }
            designation = product.getName();
        }

        SalesDocumentLine line = new SalesDocumentLine();
        line.setProduct(product);
        line.setDesignation(designation);
        line.setReference(trimToNull(request.getReference()) != null
                ? request.getReference().trim()
                : product != null ? product.getReference() : null);
        line.setQuantity(request.getQuantity());
        line.setUnitPrice(request.getUnitPrice() != null ? request.getUnitPrice()
                : product != null && product.getSalePrice() != null ? product.getSalePrice() : BigDecimal.ZERO);
        line.setDiscountRate(request.getDiscountRate() != null ? request.getDiscountRate() : BigDecimal.ZERO);

        Tax vat = request.getVatTaxId() == null ? null : references.taxes().get(request.getVatTaxId());
        line.setVatTaxId(vat == null ? null : vat.getId());
        line.setVatRate(vat == null ? BigDecimal.ZERO : vat.getRate());
        return line;
    }

    /**
     * The next number from the document type's numbering settings. The sequence row is
     * locked, so two concurrent issues never get the same number; one already taken (the
     * counter was moved by hand) is skipped.
     */
    private String nextFreeReference(SalesDocumentType type) {
        String candidate;
        do {
            candidate = numberingService.allocate(type.getNumbering());
        } while (documentRepository.existsByCompanyIdAndTypeAndReferenceIgnoreCase(
                currentUser.companyId(), type, candidate));
        return candidate;
    }

    private SalesDocumentResponse toResponse(SalesDocument document) {
        SalesDocumentResponse response = documentMapper.toResponse(document);
        addQuantities(document, response);
        if (document.getId() != null) {
            documentRepository.findBySourceIdAndCompanyIdOrderByIdAsc(document.getId(), currentUser.companyId())
                    .stream()
                    .map(documentMapper::toSummary)
                    .forEach(response.getDerived()::add);
        }
        return response;
    }

    /** The goods on the lines — a service or a free line has no stock to promise. */
    private static List<StockRequirement> goodsOf(SalesDocument document) {
        return document.getLines().stream()
                .filter(line -> line.getProduct() != null && line.getProduct().getKind() == ProductKind.GOOD)
                .map(line -> new StockRequirement(line.getProduct(), line.getQuantity()))
                .toList();
    }

    private static String sentence(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String label(SalesDocumentStatus status) {
        return status.name().toLowerCase();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record References(Map<Long, Product> products, Map<Long, Tax> taxes) {
    }
}
