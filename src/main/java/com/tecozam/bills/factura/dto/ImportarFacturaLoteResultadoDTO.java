package com.tecozam.bills.factura.dto;

/**
 * Resultado de un item individual de {@code POST /facturas/importar-lote},
 * en el mismo orden que el manifiesto recibido. Un fallo en un item
 * ({@code exito=false}, con el mensaje en {@code error}) no aborta el resto
 * del lote.
 */
public record ImportarFacturaLoteResultadoDTO(
        int indice,
        boolean exito,
        Long facturaId,
        String numFactura,
        String error
) {}
