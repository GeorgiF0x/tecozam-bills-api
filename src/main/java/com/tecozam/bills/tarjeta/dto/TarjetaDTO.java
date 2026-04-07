package com.tecozam.bills.tarjeta.dto;

import java.time.LocalDateTime;

public record TarjetaDTO(
        Long id,
        String numeroTarjeta,
        String alias,
        Long proveedorId,
        String proveedorNombre,
        String estado,
        boolean activa,
        LocalDateTime creadoEn,
        TarjetaAsignacionDTO asignacionActual
) {}
