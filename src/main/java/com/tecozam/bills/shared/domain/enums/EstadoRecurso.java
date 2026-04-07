package com.tecozam.bills.shared.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EstadoRecurso {

    DISPONIBLE("Disponible"),
    PRESTADO("Prestado"),
    BLOQUEADO("Bloqueado"),
    BAJA("Baja");

    private final String descripcion;
}
