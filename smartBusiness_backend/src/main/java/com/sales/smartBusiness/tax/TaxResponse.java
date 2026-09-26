package com.sales.smartBusiness.tax;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class TaxResponse {

    private Long id;
    private String name;
    private TaxKind kind;
    private BigDecimal rate;
    private BigDecimal amount;
    private boolean includedInVatBase;
    /** Derived from the kind — handy for the calculation engine and the screen. */
    private boolean appliesToLine;
    private boolean activeByDefault;
    private boolean active;
    private boolean system;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
