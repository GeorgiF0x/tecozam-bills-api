package com.tecozam.bills.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearUsuarioOficinaRequest(
        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Size(min = 3, max = 80, message = "El username debe tener entre 3 y 80 caracteres")
        String username,

        @NotBlank(message = "La contraseña es obligatoria")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
                message = "Mínimo 8 caracteres, con al menos una mayúscula, una minúscula y un número."
        )
        String password,

        String email,

        @NotBlank(message = "El nombre completo es obligatorio")
        String nombre,

        String dni,

        @NotBlank(message = "El rol es obligatorio")
        String rol
) {
}
