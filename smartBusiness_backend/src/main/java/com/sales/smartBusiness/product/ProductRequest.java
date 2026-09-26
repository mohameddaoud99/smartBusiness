package com.sales.smartBusiness.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Set;

@Getter
@Setter
public class ProductRequest {

    /** Optional — a code is generated ("P-0001") when this is left blank on creation. */
    @Size(max = 20, message = "Reference must not exceed 20 characters")
    private String reference;

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must not exceed 150 characters")
    private String name;

    @Size(max = 4000, message = "Description must not exceed 4000 characters")
    private String description;

    @Size(max = 60, message = "Barcode must not exceed 60 characters")
    private String barcode;

    @NotNull(message = "Kind is required")
    private ProductKind kind;

    @NotNull(message = "Purpose is required")
    private ProductPurpose purpose;

    @NotNull(message = "Unit is required")
    private ProductUnit unit;

    private Long categoryId;

    private Long brandId;

    @DecimalMin(value = "0.0", message = "Sale price cannot be negative")
    private BigDecimal salePrice;

    @DecimalMin(value = "0.0", message = "Purchase price cannot be negative")
    private BigDecimal purchasePrice;

    private boolean allowNegativeStock = true;

    @DecimalMin(value = "0.0", message = "Minimum stock cannot be negative")
    private BigDecimal minStock;

    private Set<Long> taxIds;

    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;
}
