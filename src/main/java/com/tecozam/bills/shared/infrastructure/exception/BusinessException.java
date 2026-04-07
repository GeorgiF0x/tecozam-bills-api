package com.tecozam.bills.shared.infrastructure.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final String field;

    public BusinessException(String message) {
        super(message);
        this.field = null;
    }

    public BusinessException(String message, String field) {
        super(message);
        this.field = field;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.field = null;
    }
}
