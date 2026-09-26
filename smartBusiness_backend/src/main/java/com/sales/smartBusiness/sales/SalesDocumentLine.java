package com.sales.smartBusiness.sales;

import com.sales.smartBusiness.common.BaseEntity;
import com.sales.smartBusiness.common.DocumentTotals;
import com.sales.smartBusiness.product.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A line of a sales document. Everything printed is copied here (designation, price,
 * VAT rate): the product may be renamed or repriced later, the document must not change.
 * A line without a product is a free line typed by hand.
 */
@Entity
@Table(name = "sales_document_lines")
@Getter
@Setter
@NoArgsConstructor
public class SalesDocumentLine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private SalesDocument document;

    @Column(nullable = false)
    private int position;

    /** The line of the source document this one was copied from - what quantities are followed by. Null for a line of one's own. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_line_id")
    private SalesDocumentLine sourceLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(length = 30)
    private String reference;

    @Column(nullable = false)
    private String designation;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 3)
    private BigDecimal unitPrice;

    /** Percentage taken off this line, 0–100. */
    @Column(name = "discount_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal discountRate = BigDecimal.ZERO;

    /** The VAT tax picked for the line — kept only to pre-select it when the draft is edited. */
    @Column(name = "vat_tax_id")
    private Long vatTaxId;

    @Column(name = "vat_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal vatRate = BigDecimal.ZERO;

    /** Quantity × price after the line discount, before any tax. */
    @Column(name = "line_total", nullable = false, precision = 14, scale = 3)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    void recalculate() {
        lineTotal = DocumentTotals.lineTotal(quantity, unitPrice, discountRate);
    }
}
