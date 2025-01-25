package com.spreadsheet.app.exceptions;

/**
 * Thrown when a user references a column that doesn't exist
 * in the current sheet schema.
 * For example, "Column Z not found in sheet schema".
 */
public class ColumnNotFoundException extends RuntimeException {
    public ColumnNotFoundException(String message) {
        super(message);
    }
}
