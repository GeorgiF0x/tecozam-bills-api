package com.tecozam.bills.factura.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FacturaDTO(
        Long id,
        Long proveedorId,
        String proveedorNombre,
        String numFactura,
        LocalDate fecha,
        LocalDate periodoDesde,
        LocalDate periodoHasta,
        String numCuenta,
        String nifCliente,
        String nombreCliente,
        BigDecimal baseImponible,
        BigDecimal totalIva,
        BigDecimal totalFactura,
        LocalDate vencimiento,
        String rutaPdf,
        LocalDateTime creadoEn,
        String creadoPor,
        int numTarjetas,
        int numOperaciones
) {}
