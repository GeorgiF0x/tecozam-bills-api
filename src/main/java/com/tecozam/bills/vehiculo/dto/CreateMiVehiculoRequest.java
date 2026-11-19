package com.tecozam.bills.vehiculo.dto;

import jakarta.validation.constraints.Size;

public record CreateMiVehiculoRequest(
        @Size(max = 20) String matricula,
        @Size(max = 20) String codigoObra,
        String categoria,
        @Size(max = 300) String descripcion
) {}
