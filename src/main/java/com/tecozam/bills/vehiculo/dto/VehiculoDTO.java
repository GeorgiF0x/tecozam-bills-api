package com.tecozam.bills.vehiculo.dto;

import java.time.LocalDateTime;

public record VehiculoDTO(
        Long id,
        String matricula,
        String codigoObra,
        String categoria,
        String tipo,
        String descripcion,
        String estado,
        boolean activo,
        LocalDateTime creadoEn
) {}
