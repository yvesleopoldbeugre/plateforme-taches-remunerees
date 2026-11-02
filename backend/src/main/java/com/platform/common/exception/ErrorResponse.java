package com.platform.common.exception;

/** Wire format: {@code { "error": { "code", "message", "traceId" } } } — CDCT §11. */
public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(String code, String message, String traceId) {}

    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(new ErrorBody(code, message, traceId));
    }
}
