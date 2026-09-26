package com.sales.smartBusiness.payment;

/** A payment is never deleted: a mistake is cancelled, and the invoice it was on follows. */
public enum PaymentStatus {
    ACTIVE,
    CANCELLED
}
