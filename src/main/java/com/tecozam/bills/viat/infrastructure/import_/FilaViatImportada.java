package com.tecozam.bills.viat.infrastructure.import_;

/**
 * Resultado puro de parsear una fila del Excel de import masivo de VIATs,
 * antes de tocar la base de datos. {@code errorValidacion} va relleno cuando
 * la fila no cumple las reglas de {@code ViatService.create} (codigo
 * obligatorio) — en ese caso la fila no se materializa, solo se reporta
 * como aviso.
 */
public record FilaViatImportada(
        String codigo,
        String numeroSerie,
        String descripcion,
        String errorValidacion
) {
    public boolean esValida() {
        return errorValidacion == null;
    }
}
