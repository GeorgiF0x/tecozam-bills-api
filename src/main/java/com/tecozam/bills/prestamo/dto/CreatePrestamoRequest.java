package com.tecozam.bills.prestamo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreatePrestamoRequest(
        @NotBlank String tipoRecurso,
        Long tarjetaId,
        Long viatId,
        Long vehiculoId,
        @NotNull Long trabajadorId,
        @NotNull Long centroCosteId,
        @NotBlank String tipoPrestamo,
        @NotNull LocalDateTime fechaInicio,
        LocalDateTime fechaFinPrevista,
        String observaciones
) {}
