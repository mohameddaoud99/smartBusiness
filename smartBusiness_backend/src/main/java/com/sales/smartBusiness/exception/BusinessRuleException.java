package com.sales.smartBusiness.exception;

/** Thrown when an operation is blocked by a business rule. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
