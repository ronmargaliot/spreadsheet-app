package com.spreadsheet.app.services;

import com.spreadsheet.app.exceptions.CircularReferenceException;
import com.spreadsheet.app.exceptions.InvalidTypeException;
import com.spreadsheet.app.models.ColumnDefinition;
import com.spreadsheet.app.models.ColumnType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SheetService logic, using an in-memory approach
 * (no HTTP or external server).
 */
class SheetServiceTest {

    private SheetService sheetService;
    private long sheetId;

    @BeforeEach
    void setUp() {
        sheetService = new SheetService();
        // We create a default schema of 3 columns
        List<ColumnDefinition> columns = Arrays.asList(
                new ColumnDefinition("A", ColumnType.STRING),
                new ColumnDefinition("B", ColumnType.BOOLEAN),
                new ColumnDefinition("C", ColumnType.STRING)
        );
        sheetId = sheetService.createSheet(columns);
    }

    /**
     * Verify literal sets and type mismatch.
     */
    @Test
    void testSetLiteralValues() {
        sheetService.setCellValue(sheetId, "A", 10, "hello");
        sheetService.setCellValue(sheetId, "B", 11, "true");

        // Mismatch: column B is boolean, "hello" is not
        assertThrows(InvalidTypeException.class, () ->
                sheetService.setCellValue(sheetId, "B", 12, "hello"));
    }

    /**
     * (C,1)->(A,10). Then (A,10)->(C,1) => cycle => error.
     */
    @Test
    void testLookupCycle() {
        sheetService.setCellValue(sheetId, "C", 1, "lookup(A,10)");
        sheetService.setCellValue(sheetId, "A", 10, "someValue");
        assertThrows(CircularReferenceException.class, () ->
                sheetService.setCellValue(sheetId, "A", 10, "lookup(C,1)"));
    }

    /**
     * Confirm fully evaluated data: (C,1)->"hello".
     */
    @Test
    void testEvaluateSheetData() {
        sheetService.setCellValue(sheetId, "A", 10, "hello");
        sheetService.setCellValue(sheetId, "C", 1, "lookup(A,10)");

        Map<String, Object> data = sheetService.getSheetData(sheetId);
        assertEquals("hello", data.get("C,1"));
        assertEquals("hello", data.get("A,10"));
    }

    /**
     * Single-cell cycle scenario: (A,1)->"lookup(A,1)" => revert.
     */
    @Test
    void testSingleCellCycle() {
        sheetService.setCellValue(sheetId, "A", 1, "hello");

        // Attempt cycle => fails
        assertThrows(CircularReferenceException.class, () ->
                sheetService.setCellValue(sheetId, "A", 1, "lookup(A,1)")
        );

        // Old value remains
        assertEquals("hello", sheetService.getSheetData(sheetId).get("A,1"));
    }

    /**
     * Multi-cell cycle scenario: C->A, A->B, B->C.
     */
    @Test
    void testThreeCellCycle() {
        sheetService.setCellValue(sheetId, "C", 1, "lookup(A,1)");
        sheetService.setCellValue(sheetId, "A", 1, "lookup(B,1)");

        // Now B->C => cycle
        assertThrows(CircularReferenceException.class, () ->
                sheetService.setCellValue(sheetId, "B", 1, "lookup(C,1)")
        );

        // (B,1) revert => no final value
        assertNull(sheetService.getSheetData(sheetId).get("B,1"));
    }

    /**
     * Revert logic after invalid type update.
     */
    @Test
    void testRevertAfterTypeMismatch() {
        sheetService.setCellValue(sheetId, "B", 11, "true");
        assertEquals(true, sheetService.getSheetData(sheetId).get("B,11"));

        // Mismatch => revert
        assertThrows(InvalidTypeException.class, () ->
                sheetService.setCellValue(sheetId, "B", 11, "notABoolean")
        );
        assertEquals(true, sheetService.getSheetData(sheetId).get("B,11"));
    }

    @Test
    void testChangeLiteralToLookup() {
        sheetService.setCellValue(sheetId, "A", 10, "hello");
        assertEquals("hello", sheetService.getSheetData(sheetId).get("A,10"));

        sheetService.setCellValue(sheetId, "B", 10, "true");
        assertEquals(true, sheetService.getSheetData(sheetId).get("B,10"));

        // Because (A=STRING, B=BOOLEAN), this is a mismatch
        assertThrows(InvalidTypeException.class, () ->
                sheetService.setCellValue(sheetId, "A", 10, "lookup(B,10)")
        );
        // Reverted
        assertEquals("hello", sheetService.getSheetData(sheetId).get("A,10"));
    }

    @Test
    void testChangeLookupToLiteral() {
        sheetService.setCellValue(sheetId, "A", 10, "hello");
        sheetService.setCellValue(sheetId, "C", 1, "lookup(A,10)");
        assertEquals("hello", sheetService.getSheetData(sheetId).get("C,1"));

        // Replace the lookup with a literal
        sheetService.setCellValue(sheetId, "C", 1, "newLiteral");
        Map<String, Object> data = sheetService.getSheetData(sheetId);
        assertEquals("newLiteral", data.get("C,1"));
        assertEquals("hello", data.get("A,10"));
    }

    @Test
    void testLookupUnsetCellReturnsNull() {
        sheetService.setCellValue(sheetId, "C", 1, "lookup(A,999)");
        Object val = sheetService.getSheetData(sheetId).get("C,1");
        assertNull(val, "Unset cell reference should yield null or throw, per design");
    }

    /**
     * Partial re-eval: updating (A,1) should refresh (C,1) if C->A.
     */
    @Test
    void testPartialReEvaluation() {
        sheetService.setCellValue(sheetId, "A", 1, "hi");
        sheetService.setCellValue(sheetId, "C", 1, "lookup(A,1)");
        assertEquals("hi", sheetService.getSheetData(sheetId).get("C,1"));

        sheetService.setCellValue(sheetId, "A", 1, "hello");
        assertEquals("hello", sheetService.getSheetData(sheetId).get("C,1"));
    }

    /**
     * Simple concurrency test: ensures no concurrency errors
     * when two threads set different cells simultaneously.
     */
    @Test
    void testConcurrentCellUpdates() throws InterruptedException {
        Runnable task1 = () -> sheetService.setCellValue(sheetId, "A", 10, "foo");
        Runnable task2 = () -> sheetService.setCellValue(sheetId, "B", 10, "true");

        Thread t1 = new Thread(task1);
        Thread t2 = new Thread(task2);

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        Map<String, Object> data = sheetService.getSheetData(sheetId);
        assertEquals("foo", data.get("A,10"));
        assertEquals(true, data.get("B,10"));
    }
}
