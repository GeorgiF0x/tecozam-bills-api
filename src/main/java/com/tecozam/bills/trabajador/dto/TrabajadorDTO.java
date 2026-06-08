package com.tecozam.bills.trabajador.dto;

import java.time.LocalDateTime;

public record TrabajadorDTO(
        Long id,
        String nombre,
        String apellidos,
        String email,
        String dniNie,
        boolean activo,
        LocalDateTime creadoEn
) {
}
