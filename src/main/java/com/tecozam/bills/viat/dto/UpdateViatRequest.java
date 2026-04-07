package com.tecozam.bills.viat.dto;

import jakarta.validation.constraints.Size;

public record UpdateViatRequest(
        @Size(max = 100) String numeroSerie,
        @Size(max = 300) String descripcion,
        String estado,
        Boolean activo) {
}
