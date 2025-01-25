package com.spreadsheet.app.exceptions;

/**
 * Thrown when attempting to access a sheet ID
 * that doesn't exist in the in-memory store.
 */
public class SheetNotFoundException extends RuntimeException {
    public SheetNotFoundException(String message) {
        super(message);
    }
}
