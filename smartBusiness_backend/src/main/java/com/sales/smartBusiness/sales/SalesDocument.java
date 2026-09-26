package com.sales.smartBusiness.sales;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.common.DocumentTotals;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.customer.Customer;
import com.sales.smartBusiness.tax.Tax;
import com.sales.smartBusiness.tax.TaxKind;
import com.sales.smartBusiness.warehouse.Warehouse;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/**
 * A quote, a sales order, a delivery note, an invoice or a credit note — one table, the {@link SalesDocumentType} says which.
 * Reachable only from inside its own company.
 * <p>
 * The totals are never typed: {@link #recalculate()} derives them from the lines and the
 * selected taxes, in the order the Finco analysis observed — line discount, surcharges
 * (FODEC), VAT on the base including the surcharges that enter it, then flat charges
 * (stamp duty). Everything is rounded half-up to 3 decimals (the millime).
 */
@Entity
@Table(name = "sales_documents")
@Getter
@Setter
@NoArgsConstructor
public class SalesDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SalesDocumentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SalesDocumentStatus status = SalesDocumentStatus.DRAFT;

    /** Allocated when the draft is issued, unique per company and type. Null while a draft. */
    @Column(length = 50)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Where a delivery note or an invoice takes its goods from. Null for a quote and a sales order. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    /** The document this one was made from — an order created from a quote, a delivery note from an order, an invoice from any of the three. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private SalesDocument source;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    /** For a quote, how long the offer holds. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Sum of the lines, before any tax. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal subtotal = BigDecimal.ZERO;

    /** Net to pay, all taxes included. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal total = BigDecimal.ZERO;

    /** What the credit notes issued on an invoice take off it — the sum of the live ones, rewritten by {@link #applyCredited}. */
    @Column(name = "credited_amount", nullable = false, precision = 14, scale = 3)
    private BigDecimal creditedAmount = BigDecimal.ZERO;

    /** What the customer has paid on an invoice — the sum of its active payments, rewritten by {@link #applyPaid}. */
    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 3)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** General terms of sale printed under the totals. */
    @Column(columnDefinition = "TEXT")
    private String terms;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @BatchSize(size = 50)
    private List<SalesDocumentLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @BatchSize(size = 50)
    private List<SalesDocumentTax> taxes = new ArrayList<>();

    public void addLine(SalesDocumentLine line) {
        line.setDocument(this);
        line.setPosition(lines.size());
        lines.add(line);
    }

    /**
     * Replaces the document-level taxes — surcharges and flat charges — by a snapshot of
     * the given ones. VAT is not chosen here: it comes from each line's own rate.
     */
    public void selectTaxes(Collection<Tax> selected) {
        taxes.removeIf(row -> row.getKind() != TaxKind.VAT_RATE);
        selected.stream()
                .sorted(Comparator.comparing(Tax::getKind).thenComparing(Tax::getName))
                .forEach(tax -> {
                    SalesDocumentTax row = new SalesDocumentTax();
                    row.setDocument(this);
                    row.setTaxId(tax.getId());
                    row.setKind(tax.getKind());
                    row.setName(tax.getName());
                    row.setRate(tax.getRate());
                    row.setAmount(tax.getAmount() != null ? tax.getAmount() : BigDecimal.ZERO);
                    row.setIncludedInVatBase(tax.isIncludedInVatBase());
                    taxes.add(row);
                });
    }

    /**
     * Takes the notes, terms, lines and document-level taxes of another document as they
     * are (a snapshot of a snapshot — nothing is re-read from the catalogue), then recomputes.
     */
    public void copyContentFrom(SalesDocument other) {
        copyContentFrom(other, SalesDocumentLine::getQuantity);
    }

    /**
     * The same, taking of each line only the quantity the function says - what is left of it. A line with nothing
     * left is not copied. Each copy remembers the line it came from.
     */
    public void copyContentFrom(SalesDocument other, Function<SalesDocumentLine, BigDecimal> quantityOf) {
        notes = other.notes;
        terms = other.terms;

        for (SalesDocumentLine original : other.lines) {
            BigDecimal quantity = quantityOf.apply(original);
            if (quantity.signum() <= 0) {
                continue;
            }
            SalesDocumentLine copy = new SalesDocumentLine();
            copy.setSourceLine(original);
            copy.setProduct(original.getProduct());
            copy.setReference(original.getReference());
            copy.setDesignation(original.getDesignation());
            copy.setQuantity(quantity);
            copy.setUnitPrice(original.getUnitPrice());
            copy.setDiscountRate(original.getDiscountRate());
            copy.setVatTaxId(original.getVatTaxId());
            copy.setVatRate(original.getVatRate());
            addLine(copy);
        }
        for (SalesDocumentTax original : other.taxes) {
            if (original.getKind() == TaxKind.VAT_RATE) {
                continue;
            }
            SalesDocumentTax copy = new SalesDocumentTax();
            copy.setDocument(this);
            copy.setTaxId(original.getTaxId());
            copy.setKind(original.getKind());
            copy.setName(original.getName());
            copy.setRate(original.getRate());
            copy.setAmount(original.getAmount());
            copy.setIncludedInVatBase(original.isIncludedInVatBase());
            taxes.add(copy);
        }
        recalculate();
    }

    /** Recomputes every amount from the lines and the selected taxes (see {@link DocumentTotals}). */
    public void recalculate() {
        lines.forEach(SalesDocumentLine::recalculate);

        // A TreeMap compares by value, so a rate of 19 and one of 19.000 land in the same group
        Map<BigDecimal, BigDecimal> baseByRate = new TreeMap<>();
        for (SalesDocumentLine line : lines) {
            baseByRate.merge(line.getVatRate(), line.getLineTotal(), BigDecimal::add);
        }
        List<SalesDocumentTax> surcharges = taxesOfKind(TaxKind.PERCENTAGE_SURCHARGE);
        List<SalesDocumentTax> flatCharges = taxesOfKind(TaxKind.FIXED_PER_DOCUMENT);

        DocumentTotals.Result result = DocumentTotals.compute(baseByRate,
                surcharges.stream()
                        .map(row -> new DocumentTotals.Surcharge(row.getRate(), row.isIncludedInVatBase()))
                        .toList(),
                flatCharges.stream().map(SalesDocumentTax::getAmount).toList());

        // Rows read in calculation order: surcharges, one VAT row per rate, flat charges
        List<SalesDocumentTax> ordered = new ArrayList<>();
        for (int i = 0; i < surcharges.size(); i++) {
            SalesDocumentTax surcharge = surcharges.get(i);
            surcharge.setBase(result.subtotal());
            surcharge.setAmount(result.surchargeAmounts().get(i));
            ordered.add(surcharge);
        }
        for (DocumentTotals.VatRow vat : result.vatRows()) {
            SalesDocumentTax row = new SalesDocumentTax();
            row.setDocument(this);
            row.setKind(TaxKind.VAT_RATE);
            row.setName("VAT " + vat.rate().stripTrailingZeros().toPlainString() + "%");
            row.setRate(vat.rate());
            row.setBase(vat.base());
            row.setAmount(vat.amount());
            ordered.add(row);
        }
        for (SalesDocumentTax flat : flatCharges) {
            flat.setBase(null);
            ordered.add(flat);
        }

        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition(i);
        }
        taxes.clear();
        taxes.addAll(ordered);
        subtotal = result.subtotal();
        total = result.total();
    }

    /**
     * Whether a status change is allowed. Moving out of a draft is not a status change
     * (it allocates the number) — it has its own operation, so it is refused here.
     * A quote moves freely between issued, accepted and rejected — a customer changes
     * their mind. A sales order is confirmed or cancelled, a delivery note delivered or cancelled,
     * and a cancelled document stays so.
     */
    public boolean canMoveTo(SalesDocumentStatus target) {
        if (target == status) {
            return false;
        }
        return switch (type) {
            case QUOTE -> isOneOf(status, SalesDocumentStatus.ISSUED, SalesDocumentStatus.ACCEPTED, SalesDocumentStatus.REJECTED)
                    && isOneOf(target, SalesDocumentStatus.ISSUED, SalesDocumentStatus.ACCEPTED, SalesDocumentStatus.REJECTED);
            case SALES_ORDER -> switch (status) {
                case ISSUED -> isOneOf(target, SalesDocumentStatus.CONFIRMED, SalesDocumentStatus.CANCELLED);
                case CONFIRMED -> target == SalesDocumentStatus.CANCELLED;
                default -> false;
            };
            // Created, then delivered (the goods leave the stock) — and a delivery can still be
            // cancelled, which brings the goods back
            case DELIVERY_NOTE -> switch (status) {
                case ISSUED -> isOneOf(target, SalesDocumentStatus.DELIVERED, SalesDocumentStatus.CANCELLED);
                case DELIVERED -> target == SalesDocumentStatus.CANCELLED;
                default -> false;
            };
            // Paid, partly paid: the payments decide (see applyPaid), never a status change. Only an
            // invoice nobody has paid can be cancelled — the payments are cancelled first
            case INVOICE -> status == SalesDocumentStatus.ISSUED && target == SalesDocumentStatus.CANCELLED;
            // Issued at once against its invoice; cancelling it gives the invoice back what it took off
            case CREDIT_NOTE -> status == SalesDocumentStatus.ISSUED && target == SalesDocumentStatus.CANCELLED;
            // Issued at once - its goods are back in the stock - and only cancelled, which takes them out again
            case RETURN_NOTE -> status == SalesDocumentStatus.ISSUED && target == SalesDocumentStatus.CANCELLED;
        };
    }

    /** What is left to pay on an invoice; negative when it was paid more than credit notes later left due. */
    public BigDecimal getBalance() {
        return total.subtract(creditedAmount).subtract(paidAmount);
    }

    /**
     * Sets how much of an invoice has been paid, and the status that goes with it. Called with the
     * SUM of the active payments each time one changes — the invoice is never told "add this".
     */
    public void applyPaid(BigDecimal paid) {
        paidAmount = paid;
        refreshSettlement();
    }

    /** Same for the credit notes: called with the SUM of the live ones on this invoice. */
    public void applyCredited(BigDecimal credited) {
        creditedAmount = credited;
        refreshSettlement();
    }

    /** An invoice is settled by payments AND credit notes: nothing yet, some, or all of its total. */
    private void refreshSettlement() {
        if (type != SalesDocumentType.INVOICE || isDraft() || status == SalesDocumentStatus.CANCELLED) {
            return;
        }
        BigDecimal settled = paidAmount.add(creditedAmount);
        status = settled.signum() == 0 ? SalesDocumentStatus.ISSUED
                : settled.compareTo(total) >= 0 ? SalesDocumentStatus.PAID
                : SalesDocumentStatus.PARTIALLY_PAID;
    }

    public boolean isDraft() {
        return status == SalesDocumentStatus.DRAFT;
    }

    private List<SalesDocumentTax> taxesOfKind(TaxKind kind) {
        return taxes.stream().filter(row -> row.getKind() == kind).toList();
    }

    private static boolean isOneOf(SalesDocumentStatus value, SalesDocumentStatus... candidates) {
        return Arrays.asList(candidates).contains(value);
    }
}
