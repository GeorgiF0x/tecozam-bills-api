package com.tecozam.bills.prestamo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * Petición self-service de un operario para crearse un préstamo desde la PWA.
 * El trabajador se resuelve del JWT (no se acepta en el body).
 */
public record CreateMiPrestamoRequest(
        @NotBlank String tipoRecurso,
        Long tarjetaId,
        Long viatId,
        Long vehiculoId,
        @NotNull Long centroCosteId,
        @NotNull LocalDateTime fechaInicio,
        LocalDateTime fechaFinPrevista,
        String observaciones
) {}
