package com.tecozam.bills.vehiculo.dto;

import java.time.LocalDateTime;
import java.util.List;

public record VehiculoArchivadoDTO(
        Long id,
        String matricula,
        String codigoObra,
        String tipo,
        String descripcion,
        LocalDateTime eliminadoEn,
        List<String> vinculosActivos
) {}
