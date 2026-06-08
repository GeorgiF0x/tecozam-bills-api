package com.tecozam.bills.admin.dto;

import java.util.List;

public record ImportRepsolReportDTO(
        int centrosCreados,
        int centrosExistentes,
        int trabajadoresCreados,
        int trabajadoresExistentes,
        int tarjetasCreadas,
        int tarjetasExistentes,
        int viatsCreados,
        int viatsExistentes,
        int filasIgnoradas,
        int facturasCreadas,
        int operacionesCreadas,
        List<String> errores,
        long duracionMs
) {
}
