package com.platform.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BusinessException {

    public ForbiddenException(String code, String message) {
        super(code, HttpStatus.FORBIDDEN, message);
    }
}
