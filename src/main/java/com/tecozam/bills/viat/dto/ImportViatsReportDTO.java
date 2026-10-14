package com.tecozam.bills.viat.dto;

import java.util.List;

public record ImportViatsReportDTO(
        int creados,
        int existentes,
        List<String> errores,
        long duracionMs
) {}
