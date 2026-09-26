package com.sales.smartBusiness.stock;

import com.sales.smartBusiness.product.ProductUnit;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** What a product has right now: in the warehouse, promised, and what is left to sell. */
@Getter
@Setter
public class StockLevelResponse {

    private Long productId;
    private String reference;
    private String name;
    private ProductUnit unit;
    private BigDecimal minStock;
    private BigDecimal physical;
    private BigDecimal reserved;
    /** Physical minus reserved. */
    private BigDecimal available;
    /** True once the available quantity has fallen to the minimum — company-wide, so never set when one warehouse is viewed. */
    private boolean lowStock;
}
