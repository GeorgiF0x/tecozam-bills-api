package com.tecozam.bills.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record CambiarRolRequest(
        @NotBlank(message = "El rol es obligatorio")
        String rol
) {
}
