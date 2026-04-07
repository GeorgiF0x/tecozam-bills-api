package com.tecozam.bills.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateTicketManualRequest(
        Long proveedorId,
        Long trabajadorId,
        Long tarjetaId,
        Long vehiculoId,
        @NotBlank String estacion,
        @NotNull LocalDateTime fechaHora,
        @NotNull @Positive BigDecimal importeTotal,
        BigDecimal litros,
        BigDecimal precioLitro,
        Integer kms,
        String concepto,
        String observaciones
) {}
