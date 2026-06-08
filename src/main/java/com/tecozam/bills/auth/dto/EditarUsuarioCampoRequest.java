package com.tecozam.bills.auth.dto;

public record EditarUsuarioCampoRequest(
        String nombre,
        String apellidos,
        String dni,
        String telefono
) {
}
