package com.platform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for business-rule violations exposed to the API as the
 * {@code { "error": { "code", "message", "traceId" } } } shape defined in the CDCT (§11).
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
