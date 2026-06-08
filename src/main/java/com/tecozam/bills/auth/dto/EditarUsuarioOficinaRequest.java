package com.tecozam.bills.auth.dto;

public record EditarUsuarioOficinaRequest(
        String email,
        String nombre,
        String dni
) {
}
