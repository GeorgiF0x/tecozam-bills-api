package com.tecozam.bills.vehiculo.domain;

/**
 * Discrimina si el recurso usa matrícula (VEHICULO) o código de obra (INDUSTRIAL_MAQUINARIA).
 * Determina los campos visibles en los formularios de captura y validación de tickets.
 */
public enum CategoriaRecurso {
    VEHICULO,
    INDUSTRIAL_MAQUINARIA
}
