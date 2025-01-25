package com.spreadsheet.app.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Catches custom exceptions from anywhere in the controllers or services,
 * returning user-friendly error JSON with an HTTP 4xx code instead of 500.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CircularReferenceException.class)
    public ResponseEntity<ErrorResponse> handleCircularRef(CircularReferenceException ex) {
        ErrorResponse error = new ErrorResponse("CIRCULAR_REFERENCE", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidType(InvalidTypeException ex) {
        ErrorResponse error = new ErrorResponse("INVALID_TYPE", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ColumnNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleColumnNotFound(ColumnNotFoundException ex) {
        ErrorResponse error = new ErrorResponse("COLUMN_NOT_FOUND", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SheetNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSheetNotFound(SheetNotFoundException ex) {
        ErrorResponse error = new ErrorResponse("SHEET_NOT_FOUND", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleGeneric(RuntimeException ex) {
        // Catch-all for other runtime exceptions you haven't explicitly handled
        ErrorResponse error = new ErrorResponse("SERVER_ERROR", ex.getMessage());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
