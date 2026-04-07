package com.tecozam.bills.centrocoste.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCentroCosteRequest(
        @NotBlank @Size(max = 50) String codigo,
        @NotBlank @Size(max = 120) String nombre,
        @Size(max = 300) String descripcion
) {}
