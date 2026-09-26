package com.sales.smartBusiness.customer;

/**
 * A customer is either a registered business or a private individual. The distinction
 * only changes which identifier is expected (tax number vs. national id) and how the
 * name is labelled on screen — it is one table, not two.
 */
public enum CustomerType {
    COMPANY,
    INDIVIDUAL
}
