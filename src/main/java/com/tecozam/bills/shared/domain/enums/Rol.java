package com.tecozam.bills.shared.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Rol {

    ADMIN("Administrador"),
    GESTOR("Gestor"),
    CONSULTA("Consulta");

    private final String descripcion;
}
