package com.spreadsheet.app.models;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Enumerates possible column data types:
 * STRING, INT, DOUBLE, BOOLEAN.
 */
public enum ColumnType {
    STRING,
    INT,
    DOUBLE,
    BOOLEAN;

    /**
     * Allows case-insensitive JSON input.
     * For example, "string" -> STRING, "bOolean" -> BOOLEAN, etc.
     */
    @JsonCreator
    public static ColumnType fromValue(String value) {
        // Convert user input to uppercase, then match
        return ColumnType.valueOf(value.toUpperCase());
    }
}
