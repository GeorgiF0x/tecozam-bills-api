package com.tecozam.bills.vehiculo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVehiculoRequest(
        @Size(max = 20) String matricula,
        @Size(max = 50) String codigoObra,
        String categoria,
        @NotBlank String tipo,
        @Size(max = 300) String descripcion
) {}
