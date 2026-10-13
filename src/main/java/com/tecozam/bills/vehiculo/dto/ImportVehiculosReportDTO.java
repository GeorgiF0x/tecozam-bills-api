package com.tecozam.bills.vehiculo.dto;

import java.util.List;

public record ImportVehiculosReportDTO(
        int creados,
        int existentes,
        List<String> errores,
        long duracionMs
) {}
