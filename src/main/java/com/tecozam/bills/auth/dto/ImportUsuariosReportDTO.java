package com.tecozam.bills.auth.dto;

import java.util.List;

public record ImportUsuariosReportDTO(
        int creados,
        int existentes,
        List<String> errores,
        long duracionMs
) {}
