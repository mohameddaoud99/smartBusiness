package com.sales.smartBusiness.purchase;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.tax.TaxKind;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One line of the tax breakdown under a document's lines. A snapshot: the name, rate and
 * amount are copied from the company tax when the document is saved, so a later change of
 * the finance law does not rewrite an issued document.
 */
@Entity
@Table(name = "purchase_document_taxes")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseDocumentTax extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private PurchaseDocument document;

    /** Display order: surcharges, then one VAT row per rate, then flat charges. */
    @Column(nullable = false)
    private int position;

    /** The company tax this row came from. Null for a VAT row, which is derived from the lines. */
    @Column(name = "tax_id")
    private Long taxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaxKind kind;

    @Column(nullable = false, length = 60)
    private String name;

    /** Percentage, for VAT and surcharges. Null for a flat charge. */
    @Column(precision = 6, scale = 3)
    private BigDecimal rate;

    @Column(name = "included_in_vat_base", nullable = false)
    private boolean includedInVatBase;

    /** What the tax was computed on. Null for a flat charge. */
    @Column(precision = 14, scale = 3)
    private BigDecimal base;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal amount;
}
