package com.tecozam.bills.auth.dto;

import java.time.LocalDateTime;

public record UsuarioDTO(
        Long id,
        String username,
        String rol,
        boolean activo,
        Long trabajadorId,
        String trabajadorNombre,
        LocalDateTime creadoEn
) {
}
