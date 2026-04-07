package com.tecozam.bills.shared.infrastructure.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String entityName, Long id) {
        super("%s con id %d no encontrado".formatted(entityName, id));
    }

    public ResourceNotFoundException(String entityName, String identifier) {
        super("%s '%s' no encontrado".formatted(entityName, identifier));
    }
}
