package com.tecozam.bills.trabajador.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTrabajadorRequest(
        @NotBlank
        @Size(max = 100)
        String nombre,

        @NotBlank
        @Size(max = 100)
        String apellidos,

        @Email
        String email,

        String dniNie
) {
}
