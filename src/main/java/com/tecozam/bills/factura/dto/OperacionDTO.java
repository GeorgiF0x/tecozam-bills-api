package com.tecozam.bills.factura.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OperacionDTO(
        Long id,
        String referencia,
        LocalDateTime fechaHora,
        String conceptoOriginal,
        String conceptoUnificado,
        String establecimiento,
        Integer kms,
        BigDecimal cantidad,
        BigDecimal precioNeto,
        BigDecimal precioIvaInc,
        BigDecimal importe,
        BigDecimal importeTotal,
        BigDecimal dtoPorcentaje,
        BigDecimal bonificacion,
        String tarjetaNumero,
        String conductor,
        String proveedorNombre
) {}
