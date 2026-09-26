package com.sales.smartBusiness.product;

import com.sales.smartBusiness.brand.BrandSummaryResponse;
import com.sales.smartBusiness.category.CategorySummaryResponse;
import com.sales.smartBusiness.tax.TaxSummaryResponse;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Getter
@Setter
public class ProductResponse {

    private Long id;
    private String reference;
    private String name;
    private String description;
    private String barcode;
    /** The cover photo (the first one) as a data URI — all the list screen needs. Absent when none. */
    private String imageDataUri;
    /** Every photo — filled for a single product, left empty on the list to keep it light. */
    private List<ProductImageResponse> images = new ArrayList<>();
    private ProductKind kind;
    private ProductPurpose purpose;
    private ProductUnit unit;
    private CategorySummaryResponse category;
    private BrandSummaryResponse brand;
    private BigDecimal salePrice;
    private BigDecimal purchasePrice;
    private boolean allowNegativeStock;
    private BigDecimal minStock;
    private Set<TaxSummaryResponse> defaultTaxes;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
