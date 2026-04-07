package com.tecozam.bills.viat.dto;

import java.time.LocalDateTime;

public record ViatDTO(
        Long id,
        String codigo,
        String numeroSerie,
        String descripcion,
        String estado,
        boolean activo,
        LocalDateTime creadoEn) {
}
