package com.tecozam.bills.tarifa.dto;

import java.math.BigDecimal;

public record TarifaPrecioDTO(
    Long id,
    String producto,
    String conceptoUnificado,
    BigDecimal precioSinIva,
    BigDecimal precioConIva
) {}
