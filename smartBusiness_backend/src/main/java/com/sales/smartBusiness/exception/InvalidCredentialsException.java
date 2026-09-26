package com.sales.smartBusiness.exception;

/**
 * 401 — sign-in refused. The message is shown as-is to the user, so it stays vague
 * on purpose: telling an attacker which half of the pair was wrong helps them.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
