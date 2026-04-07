package com.tecozam.bills.dashboard.dto;

import java.math.BigDecimal;

public record GastoPorProveedorDTO(
        String proveedor,
        BigDecimal importe,
        long totalFacturas
) {}
