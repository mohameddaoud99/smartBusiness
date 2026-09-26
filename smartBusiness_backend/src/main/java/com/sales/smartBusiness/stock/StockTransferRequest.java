package com.sales.smartBusiness.stock;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class StockTransferRequest {

    @NotNull(message = "Product is required")
    private Long productId;

    @NotNull(message = "Source warehouse is required")
    private Long fromWarehouseId;

    @NotNull(message = "Destination warehouse is required")
    private Long toWarehouseId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
    private BigDecimal quantity;

    @Size(max = 255, message = "Reason must not exceed 255 characters")
    private String reason;
}
