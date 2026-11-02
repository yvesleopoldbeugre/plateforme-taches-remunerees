package com.platform.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {

    public NotFoundException(String code, String message) {
        super(code, HttpStatus.NOT_FOUND, message);
    }
}
