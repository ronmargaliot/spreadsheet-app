package com.spreadsheet.app.models;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Represents an entire spreadsheet:
 * - Has a unique ID
 * - A list of ColumnDefinitions (the schema)
 * - A map of (rowIndex:columnName) -> Cell
 * - Two dependency graphs (forward, reverse) to track references
 * - A read/write lock for concurrency
 */
public class Sheet {

    // Generates unique IDs for newly created sheets
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private final long id;
    private List<ColumnDefinition> columns;
    // Key format: "<rowIndex>:<columnName>" -> Cell object
    private Map<String, Cell> cells = new ConcurrentHashMap<>();

    // Forward adjacency: "sourceCell" -> setOfCellsReferenced
    private final Map<String, Set<String>> dependencyGraphForward = new ConcurrentHashMap<>();
    // Reverse adjacency: "targetCell" -> setOfCellsThatReferenceIt
    private final Map<String, Set<String>> dependencyGraphReverse = new ConcurrentHashMap<>();

    // Lock to prevent race conditions when multiple threads update the same Sheet
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public Sheet(List<ColumnDefinition> columns) {
        this.id = ID_GENERATOR.getAndIncrement();
        this.columns = columns;
    }

    public long getId() {
        return id;
    }

    public List<ColumnDefinition> getColumns() {
        return columns;
    }

    public Map<String, Cell> getCells() {
        return cells;
    }

    /**
     * Helper to insert/update a cell in this sheet's 'cells' map.
     */
    public void setCell(Cell cell) {
        String key = generateKey(cell.getColumnName(), cell.getRowIndex());
        cells.put(key, cell);
    }

    /**
     * Retrieves the cell from the 'cells' map, if it exists.
     */
    public Cell getCell(String columnName, int rowIndex) {
        String key = generateKey(columnName, rowIndex);
        return cells.get(key);
    }

    /**
     * Builds a consistent key like "10:A" for row=10, col="A".
     */
    private String generateKey(String columnName, int rowIndex) {
        return rowIndex + ":" + columnName;
    }

    // ------------------------
    // Dependency Management
    // ------------------------

    /**
     * Adds a reference from 'source' -> 'target' in the forward graph,
     * and the reverse graph from 'target' -> 'source'.
     */
    public void addDependency(String source, String target) {
        // FORWARD: source -> target
        dependencyGraphForward
                .computeIfAbsent(source, k -> new HashSet<>())
                .add(target);
        dependencyGraphForward.putIfAbsent(target, new HashSet<>());

        // REVERSE: target -> source
        dependencyGraphReverse
                .computeIfAbsent(target, k -> new HashSet<>())
                .add(source);
        dependencyGraphReverse.putIfAbsent(source, new HashSet<>());
    }

    /**
     * Removes all forward references from 'cellKey', and
     * also removes 'cellKey' from each target's reverse references.
     */
    public void clearDependencies(String cellKey) {
        Set<String> oldTargets = dependencyGraphForward.getOrDefault(cellKey, Collections.emptySet());
        // Remove 'cellKey' from the reverse adjacency of any cells it used to reference
        for (String t : oldTargets) {
            Set<String> revSet = dependencyGraphReverse.get(t);
            if (revSet != null) {
                revSet.remove(cellKey);
            }
        }
        // Reset forward references for 'cellKey' to an empty set
        dependencyGraphForward.put(cellKey, new HashSet<>());
    }

    // Basic getters for the adjacency maps
    public Map<String, Set<String>> getForwardGraph() {
        return dependencyGraphForward;
    }
    public Map<String, Set<String>> getReverseGraph() {
        return dependencyGraphReverse;
    }

    public ReentrantReadWriteLock getLock() {
        return lock;
    }
}
