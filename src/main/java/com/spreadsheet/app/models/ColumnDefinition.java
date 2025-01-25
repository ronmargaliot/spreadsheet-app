package com.spreadsheet.app.models;

/**
 * Represents a single column in the sheet schema,
 * identified by a 'name' (e.g. "A", "B", etc.)
 * and a 'type' (STRING, BOOLEAN, etc.).
 */
public class ColumnDefinition {
    private String name;
    private ColumnType type;

    // Default constructor needed for JSON (de)serialization
    public ColumnDefinition() {
    }

    // Convenient constructor for manual instantiation
    public ColumnDefinition(String name, ColumnType type) {
        this.name = name;
        this.type = type;
    }

    // Getters/setters for each field
    public String getName() {
        return name;
    }
    public ColumnType getType() {
        return type;
    }
    public void setName(String name) {
        this.name = name;
    }
    public void setType(ColumnType type) {
        this.type = type;
    }
}
