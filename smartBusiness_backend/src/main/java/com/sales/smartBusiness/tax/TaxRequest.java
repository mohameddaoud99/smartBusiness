package com.sales.smartBusiness.tax;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * The pairing of {@code kind} with {@code rate}/{@code amount} is checked in the service,
 * where a clear business message can be raised.
 */
@Getter
@Setter
public class TaxRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 60, message = "Name must not exceed 60 characters")
    private String name;

    @NotNull(message = "Type is required")
    private TaxKind kind;

    @DecimalMin(value = "0.0", message = "Rate cannot be negative")
    @DecimalMax(value = "100.0", message = "Rate cannot exceed 100%")
    private BigDecimal rate;

    @DecimalMin(value = "0.0", message = "Amount cannot be negative")
    private BigDecimal amount;

    private boolean includedInVatBase = false;

    private boolean activeByDefault = false;

    private boolean active = true;
}
