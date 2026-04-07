package com.tecozam.bills.tarjeta.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AsignarTarjetaRequest(
        @NotNull Long trabajadorId,
        Long vehiculoId,
        @NotNull LocalDate fechaDesde,
        LocalDate fechaHasta
) {}
