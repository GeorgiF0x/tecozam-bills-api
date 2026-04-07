package com.tecozam.bills.tarjeta.dto;

import java.time.LocalDate;

public record TarjetaAsignacionDTO(
        Long id,
        Long tarjetaId,
        Long trabajadorId,
        String trabajadorNombre,
        Long vehiculoId,
        String vehiculoMatricula,
        LocalDate fechaDesde,
        LocalDate fechaHasta
) {}
