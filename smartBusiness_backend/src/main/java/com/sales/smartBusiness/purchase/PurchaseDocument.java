package com.sales.smartBusiness.purchase;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.common.DocumentTotals;
import com.sales.smartBusiness.company.Company;
import com.sales.smartBusiness.supplier.Supplier;
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
 * A purchase order, a goods receipt, a purchase invoice or a supplier credit note — one table, the {@link PurchaseDocumentType} says which.
 * Reachable only from inside its own company.
 * <p>
 * The totals are never typed: {@link #recalculate()} derives them from the lines and the
 * selected taxes, in the order the Finco analysis observed — line discount, surcharges
 * (FODEC), VAT on the base including the surcharges that enter it, then flat charges
 * (stamp duty). Everything is rounded half-up to 3 decimals (the millime).
 */
@Entity
@Table(name = "purchase_documents")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseDocumentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PurchaseDocumentStatus status = PurchaseDocumentStatus.DRAFT;

    /** Allocated when the draft is validated, unique per company and type. Null while a draft. */
    @Column(length = 50)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    /** Where a goods receipt or an invoice puts its stock. Null for a purchase order. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    /** The document this one was made from - a receipt or an invoice created from a purchase order, an invoice from a receipt. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private PurchaseDocument source;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    /** For a purchase order, the delivery date expected from the supplier. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Sum of the lines, before any tax. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal subtotal = BigDecimal.ZERO;

    /** Net to pay, all taxes included. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal total = BigDecimal.ZERO;

    /** What the credit notes validated on an invoice take off it - the sum of the live ones, rewritten by {@link #applyCredited}. */
    @Column(name = "credited_amount", nullable = false, precision = 14, scale = 3)
    private BigDecimal creditedAmount = BigDecimal.ZERO;

    /** What has been paid on an invoice - the sum of its active payments, rewritten by {@link #applyPaid}. */
    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 3)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** General terms printed under the totals. */
    @Column(columnDefinition = "TEXT")
    private String terms;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @BatchSize(size = 50)
    private List<PurchaseDocumentLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @BatchSize(size = 50)
    private List<PurchaseDocumentTax> taxes = new ArrayList<>();

    public void addLine(PurchaseDocumentLine line) {
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
                    PurchaseDocumentTax row = new PurchaseDocumentTax();
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
    public void copyContentFrom(PurchaseDocument other) {
        copyContentFrom(other, PurchaseDocumentLine::getQuantity);
    }

    /**
     * The same, taking of each line only the quantity the function says - what is left of it. A line with nothing
     * left is not copied. Each copy remembers the line it came from.
     */
    public void copyContentFrom(PurchaseDocument other, Function<PurchaseDocumentLine, BigDecimal> quantityOf) {
        notes = other.notes;
        terms = other.terms;

        for (PurchaseDocumentLine original : other.lines) {
            BigDecimal quantity = quantityOf.apply(original);
            if (quantity.signum() <= 0) {
                continue;
            }
            PurchaseDocumentLine copy = new PurchaseDocumentLine();
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
        for (PurchaseDocumentTax original : other.taxes) {
            if (original.getKind() == TaxKind.VAT_RATE) {
                continue;
            }
            PurchaseDocumentTax copy = new PurchaseDocumentTax();
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
        lines.forEach(PurchaseDocumentLine::recalculate);

        // A TreeMap compares by value, so a rate of 19 and one of 19.000 land in the same group
        Map<BigDecimal, BigDecimal> baseByRate = new TreeMap<>();
        for (PurchaseDocumentLine line : lines) {
            baseByRate.merge(line.getVatRate(), line.getLineTotal(), BigDecimal::add);
        }
        List<PurchaseDocumentTax> surcharges = taxesOfKind(TaxKind.PERCENTAGE_SURCHARGE);
        List<PurchaseDocumentTax> flatCharges = taxesOfKind(TaxKind.FIXED_PER_DOCUMENT);

        DocumentTotals.Result result = DocumentTotals.compute(baseByRate,
                surcharges.stream()
                        .map(row -> new DocumentTotals.Surcharge(row.getRate(), row.isIncludedInVatBase()))
                        .toList(),
                flatCharges.stream().map(PurchaseDocumentTax::getAmount).toList());

        // Rows read in calculation order: surcharges, one VAT row per rate, flat charges
        List<PurchaseDocumentTax> ordered = new ArrayList<>();
        for (int i = 0; i < surcharges.size(); i++) {
            PurchaseDocumentTax surcharge = surcharges.get(i);
            surcharge.setBase(result.subtotal());
            surcharge.setAmount(result.surchargeAmounts().get(i));
            ordered.add(surcharge);
        }
        for (DocumentTotals.VatRow vat : result.vatRows()) {
            PurchaseDocumentTax row = new PurchaseDocumentTax();
            row.setDocument(this);
            row.setKind(TaxKind.VAT_RATE);
            row.setName("VAT " + vat.rate().stripTrailingZeros().toPlainString() + "%");
            row.setRate(vat.rate());
            row.setBase(vat.base());
            row.setAmount(vat.amount());
            ordered.add(row);
        }
        for (PurchaseDocumentTax flat : flatCharges) {
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
     * Whether a status change is allowed. Moving out of a draft is not a status change (it
     * allocates the number and, for a receipt, puts the goods into the stock) — it has its own
     * operation, so it is refused here. A validated document can only be cancelled, and a
     * cancelled one stays so. An invoice that is partly paid or paid cannot be cancelled: its payments come first. An invoice that is partly paid or paid cannot be cancelled: its payments come first.
     */
    public boolean canMoveTo(PurchaseDocumentStatus target) {
        return status == PurchaseDocumentStatus.VALIDATED && target == PurchaseDocumentStatus.CANCELLED;
    }

    public boolean isDraft() {
        return status == PurchaseDocumentStatus.DRAFT;
    }

    /** What is left to pay the supplier on an invoice; negative when we paid more than a credit note later left due. */
    public BigDecimal getBalance() {
        return total.subtract(creditedAmount).subtract(paidAmount);
    }

    /**
     * Sets how much of an invoice has been paid, and the status that goes with it. Called with the SUM of the
     * active payments each time one changes - the invoice is never told "add this". Only an invoice that
     * stands (validated, partly paid or paid) follows its payments.
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
        if (type != PurchaseDocumentType.PURCHASE_INVOICE || isDraft() || status == PurchaseDocumentStatus.CANCELLED) {
            return;
        }
        BigDecimal settled = paidAmount.add(creditedAmount);
        status = settled.signum() == 0 ? PurchaseDocumentStatus.VALIDATED
                : settled.compareTo(total) >= 0 ? PurchaseDocumentStatus.PAID
                : PurchaseDocumentStatus.PARTIALLY_PAID;
    }

    private List<PurchaseDocumentTax> taxesOfKind(TaxKind kind) {
        return taxes.stream().filter(row -> row.getKind() == kind).toList();
    }

}
