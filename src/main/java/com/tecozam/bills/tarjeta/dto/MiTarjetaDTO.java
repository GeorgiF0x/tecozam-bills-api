package com.tecozam.bills.tarjeta.dto;

public record MiTarjetaDTO(
        Long id,
        String numeroTarjetaUltimos4,
        String alias,
        String proveedor,
        String producto,
        String centroCosteNombre,
        boolean tienePinGuardado
) {}
