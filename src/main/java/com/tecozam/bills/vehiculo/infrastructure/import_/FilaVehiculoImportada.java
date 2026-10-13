package com.tecozam.bills.vehiculo.infrastructure.import_;

/**
 * Resultado puro de parsear una fila del Excel de import masivo de vehiculos,
 * antes de tocar la base de datos. {@code errorValidacion} va relleno cuando
 * la fila no cumple las reglas de {@code VehiculoService.create} (tipo
 * obligatorio, matricula obligatoria para VEHICULO, codigoObra obligatorio
 * para INDUSTRIAL_MAQUINARIA) — en ese caso la fila no se materializa, solo
 * se reporta como aviso.
 */
public record FilaVehiculoImportada(
        String matricula,
        String codigoObra,
        String categoria,
        String tipo,
        String descripcion,
        String errorValidacion
) {
    public boolean esValida() {
        return errorValidacion == null;
    }
}
