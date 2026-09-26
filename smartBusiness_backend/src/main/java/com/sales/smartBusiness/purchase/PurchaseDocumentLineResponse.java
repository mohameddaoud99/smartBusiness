package com.sales.smartBusiness.purchase;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PurchaseDocumentLineResponse {

    private Long id;
    private Long productId;
    private String reference;
    private String designation;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountRate;
    private Long vatTaxId;
    private BigDecimal vatRate;
    private BigDecimal lineTotal;
    /** The line of the source document this one follows, if any. */
    private Long sourceLineId;
    /**
     * On a purchase order, what goods receipts have taken of this line (validated ones); on a goods receipt or an invoice,
     * what return notes have taken. Null on the other documents. {@code remainingQuantity} is what is left.
     */
    private BigDecimal fulfilledQuantity;
    private BigDecimal remainingQuantity;
    /** On a draft that follows a source line: how much of that line is still free to take, this draft excluded. */
    private BigDecimal sourceRemaining;
}
