package com.sales.smartBusiness.exception;

/** Thrown when a unique business field (username, email, code…) is already taken. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
