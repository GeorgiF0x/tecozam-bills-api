package com.tecozam.bills.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUsuarioRequest(
        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Size(max = 80, message = "El nombre de usuario no puede exceder 80 caracteres")
        String username,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String password,

        @NotBlank(message = "El rol es obligatorio")
        String rol,

        Long trabajadorId
) {
}
