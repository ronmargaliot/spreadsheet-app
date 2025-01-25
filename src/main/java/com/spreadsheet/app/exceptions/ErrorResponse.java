package com.spreadsheet.app.exceptions;

/**
 * Simple DTO to structure error responses with a code and message.
 * For example:
 * {
 *   "code": "INVALID_TYPE",
 *   "message": "Expected boolean, got java.lang.String"
 * }
 */
public class ErrorResponse {
    private String code;
    private String message;

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
