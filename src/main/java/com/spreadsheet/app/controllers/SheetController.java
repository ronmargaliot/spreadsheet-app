package com.spreadsheet.app.controllers;

import com.spreadsheet.app.models.ColumnDefinition;
import com.spreadsheet.app.models.Sheet;
import com.spreadsheet.app.services.SheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST endpoints for managing spreadsheet Sheets.
 * "/sheet" is the base path.
 */
@RestController
@RequestMapping("/sheet")
public class SheetController {

    @Autowired
    private SheetService sheetService;

    /**
     * POST /sheet
     * Expects a JSON body containing a "columns" array,
     * each with { "name", "type" }.
     * Creates a new Sheet with that schema, returns the sheetId.
     */
    @PostMapping
    public ResponseEntity<Long> createSheet(@RequestBody Map<String, List<ColumnDefinition>> request) {
        // Extract the column definitions from the request map
        List<ColumnDefinition> columns = request.get("columns");
        // Create the sheet
        long sheetId = sheetService.createSheet(columns);
        return ResponseEntity.ok(sheetId);
    }

    /**
     * PUT /sheet/{sheetId}/cell/{columnName}/{rowIndex}
     * Body: rawValue (literal or "lookup(...)").
     * Sets a cell's value in the specified sheet.
     * On success: 200 OK.
     * If there's a type mismatch or a circular reference, an exception is thrown
     * which the GlobalExceptionHandler turns into a 400.
     */
    @PutMapping("/{sheetId}/cell/{columnName}/{rowIndex}")
    public ResponseEntity<Void> setCellValue(
            @PathVariable long sheetId,
            @PathVariable String columnName,
            @PathVariable int rowIndex,
            @RequestBody String rawValue
    ) {
        sheetService.setCellValue(sheetId, columnName, rowIndex, rawValue);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /sheet/{sheetId}
     * Returns a map of fully evaluated cell values for the sheet,
     * in the format: { "A,10": "hello", "B,2": 42, ... }.
     */
    @GetMapping("/{sheetId}")
    public ResponseEntity<Map<String, Object>> getSheet(@PathVariable long sheetId) {
        Map<String, Object> data = sheetService.getSheetData(sheetId);
        return ResponseEntity.ok(data);
    }

    /**
     * GET /sheet/{sheetId}/dependencies
     * Returns the forward dependency graph of the sheet,
     * i.e., for each cell => the set of cells it references.
     */
    @GetMapping("/{sheetId}/forwardDependencies")
    public ResponseEntity<Map<String, Set<String>>> getForwardDependencyGraph(@PathVariable long sheetId) {
        Sheet sheet = sheetService.getSheet(sheetId);
        // We return the forward adjacency: cellKey -> setOfReferencedCells
        return ResponseEntity.ok(sheet.getForwardGraph());
    }

    /**
     * GET /sheet/{sheetId}/dependencies
     * Returns the reverse dependency graph of the sheet,
     * i.e., for each cell => the set of cells it references.
     */
    @GetMapping("/{sheetId}/reverseDependencies")
    public ResponseEntity<Map<String, Set<String>>> getReverseDependencyGraph(@PathVariable long sheetId) {
        Sheet sheet = sheetService.getSheet(sheetId);
        // We return the forward adjacency: cellKey -> setOfReferencedCells
        return ResponseEntity.ok(sheet.getReverseGraph());
    }
}
