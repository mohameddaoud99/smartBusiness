package com.sales.smartBusiness.stock;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the dashboard says of the stock: what it is worth at purchase price, and the goods that have fallen to their
 * minimum - how many, and the first few.
 */
public record StockFigures(BigDecimal value, long lowStockCount, List<StockLevelResponse> lowStock) {
}
