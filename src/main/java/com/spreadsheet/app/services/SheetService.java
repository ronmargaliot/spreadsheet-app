package com.spreadsheet.app.services;

import com.spreadsheet.app.exceptions.CircularReferenceException;
import com.spreadsheet.app.exceptions.ColumnNotFoundException;
import com.spreadsheet.app.exceptions.InvalidTypeException;
import com.spreadsheet.app.exceptions.SheetNotFoundException;
import com.spreadsheet.app.models.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main business logic for creating sheets, setting cell values,
 * evaluating lookups, and detecting cycles.
 */
@Service
public class SheetService {

    // All sheets live here in memory; no persistent DB in this example
    private final Map<Long, Sheet> sheets = new ConcurrentHashMap<>();

    // Regex to identify a lookup expression: "lookup(A, 10)"
    private static final Pattern LOOKUP_PATTERN = Pattern.compile("^\\s*lookup\\((\\w+),\\s*(\\d+)\\)\\s*$");

    /**
     * Creates a new Sheet with the given column definitions and returns its ID.
     */
    public long createSheet(List<ColumnDefinition> columns) {
        Sheet sheet = new Sheet(columns);
        sheets.put(sheet.getId(), sheet);
        return sheet.getId();
    }

    /**
     * Retrieves a Sheet by ID. Throws if not found.
     */
    public Sheet getSheet(long sheetId) {
        Sheet sheet = sheets.get(sheetId);
        if (sheet == null) {
            // We do throw a generic RuntimeException here,
            // but you could define a specific SheetNotFoundException if desired.
            throw new SheetNotFoundException("Sheet not found: " + sheetId);
        }
        return sheet;
    }

    /**
     * Sets a cell's value (literal or lookup) with these steps:
     * 1) Confirm column name exists (else throw ColumnNotFoundException).
     * 2) Prepare to revert if something fails (type mismatch, cycle, etc.).
     * 3) Detect if it's a lookup or literal; evaluate type.
     * 4) Detect cycles.
     * 5) If all good, finalize and re-evaluate dependents.
     */
    public void setCellValue(long sheetId, String columnName, int rowIndex, String rawValue) {
        Sheet sheet = getSheet(sheetId);

        // Prevent race conditions among multiple writers
        sheet.getLock().writeLock().lock();
        try {
            // 1) Ensure the column truly exists in the sheet
            ColumnDefinition columnDef = findColumnDefinition(sheet, columnName);
            if (columnDef == null) {
                throw new ColumnNotFoundException("Column " + columnName + " not found in sheet schema");
            }

            String cellKey = buildKey(columnName, rowIndex);
            Map<String, Set<String>> graph = sheet.getForwardGraph();

            // Keep the old dependencies for revert if needed
            graph.putIfAbsent(cellKey, new HashSet<>());
            Set<String> oldDependencies = new HashSet<>(graph.get(cellKey));

            // Keep the old cell for revert if needed
            Cell oldCell = sheet.getCells().get(cellKey);

            // Create a new cell placeholder
            Cell newCell = new Cell(columnName, rowIndex, rawValue);
            sheet.getCells().put(cellKey, newCell);

            // Clear out any old references (forward+reverse) for this cell
            sheet.clearDependencies(cellKey);

            try {
                // Check if it's "lookup(...)" or a literal
                Matcher matcher = LOOKUP_PATTERN.matcher(rawValue);
                if (matcher.matches()) {
                    // 4) Evaluate once for type check
                    String lookupCol = matcher.group(1);
                    int lookupRow = Integer.parseInt(matcher.group(2));

                    Object evaluated = evaluateLookup(sheet, lookupCol, lookupRow, new HashSet<>());
                    checkTypeCompatibility(columnDef.getType(), evaluated);

                    // Check for cycles
                    detectCycle(sheet, newCell, new HashSet<>());

                    // Add forward+reverse edges
                    sheet.addDependency(cellKey, buildKey(lookupCol, lookupRow));
                } else {
                    // 5) Parse literal
                    Object typedValue = parseLiteralValue(columnDef.getType(), rawValue);
                    checkTypeCompatibility(columnDef.getType(), typedValue);

                    detectCycle(sheet, newCell, new HashSet<>());
                }

                // If no exception, finalize
                newCell.setRawValue(rawValue);

                // Compute the final value now (no repeated DFS on GET)
                Object finalValue = evaluateCell(sheet, newCell, new HashSet<>());
                newCell.setEvaluatedValue(finalValue);

                // Partially re-evaluate only the cells that depend on this one
                reEvaluateDependents(sheet, cellKey);

            } catch (Exception ex) {
                // If something failed (type mismatch, cycle, etc.), revert
                if (oldCell == null) {
                    sheet.getCells().remove(cellKey);
                } else {
                    sheet.getCells().put(cellKey, oldCell);
                }
                graph.put(cellKey, oldDependencies);

                throw ex; // let the controller handle it
            }
        } finally {
            sheet.getLock().writeLock().unlock();
        }
    }

    /**
     * Returns a map of (columnName,rowIndex) -> evaluatedValue for all cells.
     * No DFS here, because we store each cell's final value at set time.
     */
    public Map<String, Object> getSheetData(long sheetId) {
        Sheet sheet = getSheet(sheetId);

        sheet.getLock().readLock().lock();
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            for (Cell cell : sheet.getCells().values()) {
                // Return pre-computed values
                data.put(cell.getColumnName() + "," + cell.getRowIndex(),
                        cell.getEvaluatedValue());
            }
            return data;
        } finally {
            sheet.getLock().readLock().unlock();
        }
    }

    // ----------------------------------------------------------------
    // Internal Helpers (used within this service only)
    // ----------------------------------------------------------------

    private ColumnDefinition findColumnDefinition(Sheet sheet, String columnName) {
        return sheet.getColumns().stream()
                .filter(cd -> cd.getName().equals(columnName))
                .findFirst()
                .orElse(null); // We handle null by throwing ColumnNotFoundException
    }

    private String buildKey(String columnName, int rowIndex) {
        return rowIndex + ":" + columnName;
    }

    /**
     * Converts a string (rawValue) to the correct object type (String, Boolean, Int, Double).
     * Throws InvalidTypeException if the conversion fails.
     */
    private Object parseLiteralValue(ColumnType type, String rawValue) {
        switch (type) {
            case STRING:
                return rawValue;
            case BOOLEAN:
                if ("true".equalsIgnoreCase(rawValue) || "false".equalsIgnoreCase(rawValue)) {
                    return Boolean.valueOf(rawValue);
                }
                throw new InvalidTypeException("Expected boolean, got: " + rawValue);
            case INT:
                try {
                    return Integer.parseInt(rawValue);
                } catch (NumberFormatException e) {
                    throw new InvalidTypeException("Expected int, got: " + rawValue);
                }
            case DOUBLE:
                try {
                    return Double.parseDouble(rawValue);
                } catch (NumberFormatException e) {
                    throw new InvalidTypeException("Expected double, got: " + rawValue);
                }
            default:
                // This branch shouldn't occur in your code, but we keep it for completeness
                throw new InvalidTypeException("Unsupported column type: " + type);
        }
    }

    /**
     * Evaluates a single cell:
     * - Detects cycles by checking visited
     * - If it's a lookup, follow references
     * - Otherwise parse as literal
     * - Stores the result in cell's evaluatedValue
     */
    private Object evaluateCell(Sheet sheet, Cell cell, Set<String> visited) {
        String cellKey = buildKey(cell.getColumnName(), cell.getRowIndex());
        if (visited.contains(cellKey)) {
            throw new CircularReferenceException("Cycle detected at " + cellKey);
        }
        visited.add(cellKey);

        Matcher matcher = LOOKUP_PATTERN.matcher(cell.getRawValue());
        Object evaluatedValue;
        if (matcher.matches()) {
            String lookupCol = matcher.group(1);
            int lookupRow = Integer.parseInt(matcher.group(2));
            evaluatedValue = evaluateLookup(sheet, lookupCol, lookupRow, visited);
        } else {
            ColumnDefinition columnDef = findColumnDefinition(sheet, cell.getColumnName());
            evaluatedValue = parseLiteralValue(columnDef.getType(), cell.getRawValue());
        }
        cell.setEvaluatedValue(evaluatedValue);
        return evaluatedValue;
    }

    /**
     * Evaluate a lookup(A,10) by retrieving the referenced cell
     * and calling evaluateCell on it (with cycle checks).
     */
    private Object evaluateLookup(Sheet sheet, String lookupCol, int lookupRow, Set<String> visited) {
        Cell refCell = sheet.getCell(lookupCol, lookupRow);
        if (refCell == null) {
            // referencing an unset cell => treat as null or optionally throw
            return null;
        }
        return evaluateCell(sheet, refCell, visited);
    }

    /**
     * Throws InvalidTypeException if actualValue doesn't match the column's expected type.
     */
    private void checkTypeCompatibility(ColumnType expectedType, Object actualValue) {
        if (actualValue == null) {
            // We allow null for a reference to an unset cell
            return;
        }
        switch (expectedType) {
            case STRING:
                if (!(actualValue instanceof String)) {
                    throw new InvalidTypeException("Expected STRING, got " + actualValue.getClass());
                }
                break;
            case BOOLEAN:
                if (!(actualValue instanceof Boolean)) {
                    throw new InvalidTypeException("Expected BOOLEAN, got " + actualValue.getClass());
                }
                break;
            case INT:
                if (!(actualValue instanceof Integer)) {
                    throw new InvalidTypeException("Expected INT, got " + actualValue.getClass());
                }
                break;
            case DOUBLE:
                if (!(actualValue instanceof Double)) {
                    throw new InvalidTypeException("Expected DOUBLE, got " + actualValue.getClass());
                }
                break;
        }
    }

    /**
     * If newCell references something that eventually references newCell, throw.
     * Basic DFS approach for cycle detection (1 or more cells in a loop).
     */
    private void detectCycle(Sheet sheet, Cell newCell, Set<String> visited) {
        String newCellKey = buildKey(newCell.getColumnName(), newCell.getRowIndex());
        if (visited.contains(newCellKey)) {
            throw new CircularReferenceException("Cycle detected for " + newCellKey);
        }
        visited.add(newCellKey);

        Matcher matcher = LOOKUP_PATTERN.matcher(newCell.getRawValue());
        if (matcher.matches()) {
            String lookupCol = matcher.group(1);
            int lookupRow = Integer.parseInt(matcher.group(2));
            evaluateLookup(sheet, lookupCol, lookupRow, visited);
        }
    }

    /**
     * Partially re-evaluates only those cells that depend on 'startCellKey',
     * using the reverse adjacency. This ensures they see the updated value.
     */
    private void reEvaluateDependents(Sheet sheet, String startCellKey) {
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        queue.add(startCellKey);
        visited.add(startCellKey);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            // The reverse graph: who references 'current' directly?
            Set<String> children = sheet.getReverseGraph().getOrDefault(current, Collections.emptySet());
            for (String child : children) {
                if (!visited.contains(child)) {
                    Cell childCell = sheet.getCells().get(child);
                    if (childCell != null) {
                        evaluateCell(sheet, childCell, new HashSet<>());
                    }
                    queue.add(child);
                    visited.add(child);
                }
            }
        }
    }
}
