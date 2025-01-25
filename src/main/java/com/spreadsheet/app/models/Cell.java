package com.spreadsheet.app.models;

/**
 * Represents a single spreadsheet cell.
 * Stores:
 * - which column (columnName) and which row (rowIndex)
 * - rawValue (literal or "lookup" expression)
 * - evaluatedValue (the final computed result)
 * - dirty flag to signal if the cached evaluatedValue is stale
 */
public class Cell {
    private String columnName;
    private int rowIndex;
    // rawValue could be a literal like "42" or "true", or a function call like "lookup(A,10)"
    private String rawValue;
    private Object evaluatedValue; // final computed value after type-check / lookup
    private boolean dirty = true;  // indicates if evaluatedValue needs re-computation

    public Cell(String columnName, int rowIndex, String rawValue) {
        this.columnName = columnName;
        this.rowIndex = rowIndex;
        this.rawValue = rawValue;
    }

    // Basic getters
    public String getColumnName() {
        return columnName;
    }
    public int getRowIndex() {
        return rowIndex;
    }
    public String getRawValue() {
        return rawValue;
    }

    // Set rawValue if the cell changes
    public void setRawValue(String rawValue) {
        this.rawValue = rawValue;
    }

    // The final computed (and potentially cached) value
    public Object getEvaluatedValue() {
        return evaluatedValue;
    }

    public void setEvaluatedValue(Object evaluatedValue) {
        this.evaluatedValue = evaluatedValue;
        this.dirty = false; // Once computed, mark no longer dirty
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
    public boolean isDirty() {
        return dirty;
    }
}
