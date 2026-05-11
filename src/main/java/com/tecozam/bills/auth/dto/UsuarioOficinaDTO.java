package com.tecozam.bills.auth.dto;

import java.time.LocalDateTime;

public record UsuarioOficinaDTO(
        Long id,
        String username,
        String email,
        String rol,
        boolean activo,
        String estadoRegistro,
        LocalDateTime creadoEn
) {
}
