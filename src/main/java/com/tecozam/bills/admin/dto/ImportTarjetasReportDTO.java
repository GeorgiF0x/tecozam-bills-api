package com.tecozam.bills.admin.dto;

import java.util.List;

public record ImportTarjetasReportDTO(
        int centrosCreados,
        int centrosExistentes,
        int trabajadoresCreados,
        int trabajadoresExistentes,
        int tarjetasCreadas,
        int tarjetasExistentes,
        List<String> errores,
        long duracionMs
) {
}
