package com.tecozam.bills.trabajador.dto;

import java.util.List;

public record ImportTrabajadoresReportDTO(
        int creados,
        int existentes,
        List<String> errores,
        long duracionMs
) {}
