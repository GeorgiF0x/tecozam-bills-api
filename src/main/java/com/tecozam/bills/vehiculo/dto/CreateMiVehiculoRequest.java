package com.tecozam.bills.vehiculo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMiVehiculoRequest(
        @NotBlank @Size(max = 20) String matricula,
        @Size(max = 300) String descripcion
) {}
