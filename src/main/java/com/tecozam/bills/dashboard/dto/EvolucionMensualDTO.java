package com.tecozam.bills.dashboard.dto;

import java.math.BigDecimal;

public record EvolucionMensualDTO(
        String mes,
        BigDecimal importe,
        long numOperaciones
) {}
