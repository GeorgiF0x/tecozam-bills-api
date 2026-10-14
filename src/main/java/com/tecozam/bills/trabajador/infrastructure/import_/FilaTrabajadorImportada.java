package com.tecozam.bills.trabajador.infrastructure.import_;

import com.tecozam.bills.trabajador.domain.OrigenTrabajador;

/**
 * Resultado puro de parsear una fila del Excel de import masivo de
 * trabajadores, antes de tocar la base de datos. {@code errorValidacion} va
 * relleno cuando faltan nombre o apellidos (ambos obligatorios) — en ese caso
 * la fila no se materializa, solo se reporta como aviso.
 */
public record FilaTrabajadorImportada(
        String nombre,
        String apellidos,
        String email,
        String dniNie,
        OrigenTrabajador origen,
        String errorValidacion
) {
    public boolean esValida() {
        return errorValidacion == null;
    }
}
