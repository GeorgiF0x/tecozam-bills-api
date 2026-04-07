package com.tecozam.bills.shared.infrastructure.exception;

public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String entityName, String field, Object value) {
        super(
            "%s con %s '%s' ya existe".formatted(entityName, field, value),
            field
        );
    }
}
