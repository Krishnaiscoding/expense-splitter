package com.chegg.expensesplitter.exception;

/**
 * Thrown for business-rule validation failures that should map to HTTP 422
 * (e.g. paidBy not a group member, splitAmong contains non-members).
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}
