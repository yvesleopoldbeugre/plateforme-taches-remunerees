package com.platform.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Used for state-conflict errors such as {@code TASK_FULL}, {@code ALREADY_PARTICIPATING}
 * or {@code IDEMPOTENCY_KEY_CONFLICT} (CDCT §11).
 */
public class ConflictException extends BusinessException {

    public ConflictException(String code, String message) {
        super(code, HttpStatus.CONFLICT, message);
    }
}
