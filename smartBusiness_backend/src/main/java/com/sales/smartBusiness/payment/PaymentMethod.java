package com.sales.smartBusiness.payment;

import lombok.Getter;

/**
 * How a customer paid. Withholding tax, and the follow-up of a cheque until it is cashed, are not
 * modelled yet: a cheque counts as paid the day it is recorded.
 */
@Getter
public enum PaymentMethod {

    CASH("Cash"),
    BANK_TRANSFER("Bank transfer"),
    CHECK("Cheque"),
    CARD("Bank card"),
    OTHER("Other");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }
}
