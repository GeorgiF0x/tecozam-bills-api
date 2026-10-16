package com.tecozam.bills.auth.dto;

import java.time.LocalDateTime;

public record InvitacionPendienteDTO(
        Long id,
        String nombre,
        String email,
        String invitadoPorUsername,
        LocalDateTime expiresAt
) {
}
