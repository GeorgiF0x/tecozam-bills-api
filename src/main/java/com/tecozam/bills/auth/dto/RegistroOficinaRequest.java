package com.tecozam.bills.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroOficinaRequest(

        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Size(min = 3, max = 80, message = "El username debe tener entre 3 y 80 caracteres")
        String username,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
        String password,

        String email,

        /** Nombre completo (nombre + apellidos). Se separa al crear el Trabajador en la aprobación. */
        String nombre,

        /** DNI / NIE / documento extranjero. Opcional. */
        String dni
) {
}
