package com.tecozam.bills.tarjeta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTarjetaRequest(
        @NotBlank @Size(max = 50) String numeroTarjeta,
        String alias,
        @NotNull Long proveedorId
) {}
