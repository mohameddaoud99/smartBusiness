package com.sales.smartBusiness.stock;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class StockMovementResponse {

    private Long id;
    private StockMovementType type;
    private Long productId;
    private String productReference;
    private String productName;
    private Long warehouseId;
    private String warehouseName;
    /** Signed: positive added to the stock, negative removed from it. */
    private BigDecimal quantity;
    private String reason;
    private StockSource sourceType;
    private Long sourceId;
    private LocalDateTime occurredAt;
}
