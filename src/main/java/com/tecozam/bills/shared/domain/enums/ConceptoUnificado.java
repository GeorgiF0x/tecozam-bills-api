package com.tecozam.bills.shared.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ConceptoUnificado {

    DIESEL("Diésel"),
    GASOLINA("Gasolina"),
    ADBLUE("AdBlue"),
    PEAJE("Peaje"),
    LAVADO("Lavado"),
    LUBRICANTE("Lubricante"),
    OTROS("Otros"),
    DESCUENTO("Descuento");

    private final String descripcion;
}
