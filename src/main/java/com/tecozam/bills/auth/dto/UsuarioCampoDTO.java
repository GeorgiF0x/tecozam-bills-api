package com.tecozam.bills.auth.dto;

import java.time.LocalDateTime;

public record UsuarioCampoDTO(
        Long id,
        String username,
        String telefono,
        String nombre,
        String apellidos,
        Long trabajadorId,
        boolean activo,
        String estadoRegistro,
        LocalDateTime creadoEn
) {
}
