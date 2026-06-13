package com.tecozam.bills.tarjeta.dto;

/**
 * Body de POST /api/tarjetas/{id}/pin/reveal. Debe llegar UNO de los dos:
 * un assertion (resultado de navigator.credentials.get) o el password del
 * usuario CAMPO como fallback. Si llegan ambos, prevalece la assertion.
 */
public record RevealPinRequest(
        AssertionPart assertion,
        String password
) {
    public record AssertionPart(String token, String credentialJson) {}
}
