package com.tecozam.bills.viat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateViatRequest(
        @NotBlank @Size(max = 50) String codigo,
        @Size(max = 100) String numeroSerie,
        @Size(max = 300) String descripcion) {
}
