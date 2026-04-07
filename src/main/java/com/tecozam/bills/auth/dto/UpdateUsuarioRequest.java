package com.tecozam.bills.auth.dto;

import jakarta.validation.constraints.Size;

public record UpdateUsuarioRequest(
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password,

        String rol,

        Long trabajadorId
) {
}
