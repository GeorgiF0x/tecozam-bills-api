package com.tecozam.bills.prestamo.dto;

import java.time.LocalDateTime;

public record PrestamoDTO(
        Long id,
        String tipoRecurso,
        Long tarjetaId,
        Long viatId,
        Long vehiculoId,
        String recursoDescripcion,
        Long trabajadorId,
        String trabajadorNombre,
        Long centroCosteId,
        String centroCosteNombre,
        String tipoPrestamo,
        String estado,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFinPrevista,
        LocalDateTime fechaDevolucionReal,
        String observaciones,
        boolean creadoPorCampo,
        LocalDateTime creadoEn
) {}
