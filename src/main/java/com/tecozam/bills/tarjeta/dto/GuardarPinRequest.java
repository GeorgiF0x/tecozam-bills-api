package com.tecozam.bills.tarjeta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GuardarPinRequest(
        @NotBlank
        @Pattern(regexp = "^\\d{4}$", message = "El PIN debe tener exactamente 4 dígitos numéricos")
        String pin
) {}
