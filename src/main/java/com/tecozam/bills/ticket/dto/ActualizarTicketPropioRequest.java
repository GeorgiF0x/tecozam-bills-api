package com.tecozam.bills.ticket.dto;

import com.tecozam.bills.ticket.domain.TipoGasto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Edicion de un ticket propio en PENDIENTE (operario corrigiendo su propio error). */
public record ActualizarTicketPropioRequest(
        @NotBlank String estacion,
        @NotNull LocalDateTime fechaHora,
        @NotNull @Positive BigDecimal importeTotal,
        BigDecimal litros,
        BigDecimal precioLitro,
        @NotNull TipoGasto tipoGasto
) {}
