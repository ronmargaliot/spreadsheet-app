package com.spreadsheet.app.exceptions;

/**
 * Thrown when a cell's provided value doesn't match the
 * expected column data type (e.g., assigning "hello" to a boolean column).
 */
public class InvalidTypeException extends RuntimeException {
    public InvalidTypeException(String message) {
        super(message);
    }
}
