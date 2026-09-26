package com.sales.smartBusiness.stock;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A movement recorded by hand: an entry, an exit, or an inventory adjustment. For an
 * adjustment {@code quantity} is the quantity COUNTED in the warehouse — the movement
 * written is the difference with what the register says.
 */
@Getter
@Setter
public class StockMovementRequest {

    @NotNull(message = "Type is required")
    private StockMovementType type;

    @NotNull(message = "Product is required")
    private Long productId;

    @NotNull(message = "Warehouse is required")
    private Long warehouseId;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.0", message = "Quantity cannot be negative")
    private BigDecimal quantity;

    @Size(max = 255, message = "Reason must not exceed 255 characters")
    private String reason;
}
