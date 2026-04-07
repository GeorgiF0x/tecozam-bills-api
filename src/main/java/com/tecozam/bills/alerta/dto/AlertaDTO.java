package com.tecozam.bills.alerta.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AlertaDTO(
        Long id,
        Long prestamoId,
        String tipoAlerta,
        LocalDate fechaAlerta,
        String mensaje,
        boolean emailEnviado,
        boolean leida,
        LocalDateTime creadoEn,
        String recursoDescripcion,
        String trabajadorNombre
) {}
