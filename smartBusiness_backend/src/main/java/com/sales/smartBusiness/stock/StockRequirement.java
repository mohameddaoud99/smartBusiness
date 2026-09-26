package com.sales.smartBusiness.stock;

import com.sales.smartBusiness.product.Product;

import java.math.BigDecimal;

/** A quantity of a product a document needs — what a confirmed order asks the stock to reserve. */
public record StockRequirement(Product product, BigDecimal quantity) {
}
