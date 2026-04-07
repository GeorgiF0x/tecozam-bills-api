package com.tecozam.bills.trabajador.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateTrabajadorRequest(
        @Size(max = 100)
        String nombre,

        @Size(max = 100)
        String apellidos,

        @Email
        String email,

        Boolean activo
) {
}
