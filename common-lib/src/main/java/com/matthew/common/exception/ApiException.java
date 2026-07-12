package com.matthew.common.exception;

/**
 * Base exception for all API-related errors
 */
public class ApiException extends RuntimeException {
    private final String errorCode;
    private final int httpStatus;

    public ApiException(String message) {
        super(message);
        this.errorCode = "INTERNAL_ERROR";
        this.httpStatus = 500;
    }

    public ApiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = 500;
    }

    public ApiException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "INTERNAL_ERROR";
        this.httpStatus = 500;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
