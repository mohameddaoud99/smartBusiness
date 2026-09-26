package com.sales.smartBusiness.purchase;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PurchaseDocumentLineRequest {

    /** Null for a free line typed by hand. */
    private Long productId;

    /** Left blank, a product line takes the product code. */
    @Size(max = 30, message = "Reference must not exceed 30 characters")
    private String reference;

    /** Required for a free line; a product line takes the product name when this is blank. */
    @Size(max = 255, message = "Designation must not exceed 255 characters")
    private String designation;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
    private BigDecimal quantity;

    /** Left empty, a product line takes the product purchase price. */
    @DecimalMin(value = "0.0", message = "Unit price cannot be negative")
    private BigDecimal unitPrice;

    @DecimalMin(value = "0.0", message = "Discount cannot be negative")
    @DecimalMax(value = "100.0", message = "Discount cannot exceed 100%")
    private BigDecimal discountRate;

    /** A VAT rate of the company. Left empty, the line carries no VAT. */
    private Long vatTaxId;

    /** The line of the source document this one follows. Kept from the draft made from that document; never typed. */
    private Long sourceLineId;
}
