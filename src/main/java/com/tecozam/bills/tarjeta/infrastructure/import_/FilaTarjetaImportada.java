package com.tecozam.bills.tarjeta.infrastructure.import_;

/**
 * Resultado puro de parsear una fila del Excel de alta masiva de tarjetas,
 * antes de tocar la base de datos. {@code errorValidacion} va relleno cuando
 * la fila no cumple las reglas de {@code TarjetaService.create} (numero de
 * tarjeta y proveedor obligatorios) — en ese caso la fila no se materializa,
 * solo se reporta como aviso.
 */
public record FilaTarjetaImportada(
        String numeroTarjeta,
        String alias,
        String codigoProveedor,
        String errorValidacion
) {
    public boolean esValida() {
        return errorValidacion == null;
    }
}
