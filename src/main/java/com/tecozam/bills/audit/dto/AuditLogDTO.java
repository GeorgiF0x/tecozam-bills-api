package com.tecozam.bills.audit.dto;

import java.time.LocalDateTime;

public record AuditLogDTO(
    Long id,
    String tabla,
    Long registroId,
    String accion,
    Long usuarioId,
    String datosAnteriores,
    String datosNuevos,
    LocalDateTime fecha
) {}
