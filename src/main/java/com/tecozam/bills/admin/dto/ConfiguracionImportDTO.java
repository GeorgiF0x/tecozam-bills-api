package com.tecozam.bills.admin.dto;

import java.time.LocalDateTime;

public record ConfiguracionImportDTO(
        boolean modoLlmActivo,
        LocalDateTime actualizadoEn,
        String actualizadoPor
) {}
