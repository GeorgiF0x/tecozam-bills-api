package com.tecozam.bills.centrocoste.dto;

import java.time.LocalDateTime;

public record CentroCosteDTO(
        Long id,
        String codigo,
        String nombre,
        String descripcion,
        boolean activo,
        LocalDateTime creadoEn
) {}
