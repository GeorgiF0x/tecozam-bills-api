package com.tecozam.bills.vehiculo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVehiculoRequest(
        @NotBlank @Size(max = 20) String matricula,
        @NotBlank String tipo,
        @Size(max = 300) String descripcion
) {}
