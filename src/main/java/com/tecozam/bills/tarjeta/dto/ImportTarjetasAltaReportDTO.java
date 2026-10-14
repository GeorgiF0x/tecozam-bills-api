package com.tecozam.bills.tarjeta.dto;

import java.util.List;

public record ImportTarjetasAltaReportDTO(
        int creados,
        int existentes,
        List<String> errores,
        long duracionMs
) {}
