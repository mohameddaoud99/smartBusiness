package com.sales.smartBusiness.stock;

import lombok.Getter;

/**
 * What a movement is. ENTRY, EXIT, ADJUSTMENT and the two transfer legs change what is
 * physically in the warehouse; RESERVE and RELEASE change what is promised to customers
 * without moving anything.
 */
@Getter
public enum StockMovementType {

    ENTRY("Entry", true),
    EXIT("Exit", true),
    ADJUSTMENT("Adjustment", true),
    TRANSFER_IN("Transfer in", true),
    TRANSFER_OUT("Transfer out", true),
    RESERVE("Reserved", false),
    RELEASE("Released", false);

    private final String label;
    private final boolean physical;

    StockMovementType(String label, boolean physical) {
        this.label = label;
        this.physical = physical;
    }
}
