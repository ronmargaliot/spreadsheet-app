package com.spreadsheet.app.exceptions;

/**
 * Thrown when attempting to create or resolve a cell reference
 * leading to a circular dependency (e.g., a cell referencing itself,
 * or a multi-cell loop).
 */
public class CircularReferenceException extends RuntimeException {
    public CircularReferenceException(String message) {
        super(message);
    }
}
