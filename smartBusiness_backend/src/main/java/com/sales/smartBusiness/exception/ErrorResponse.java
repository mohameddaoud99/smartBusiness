package com.sales.smartBusiness.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ErrorResponse {

    private int status;
    private String message;
    private List<String> errors;

    public ErrorResponse(int status, String message) {
        this(status, message, null);
    }
}
