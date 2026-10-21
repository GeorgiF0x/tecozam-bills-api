package com.tecozam.bills.factura.dto;

/**
 * Un item del manifiesto JSON recibido en {@code POST /facturas/importar-lote}.
 * {@code facturaKey}/{@code extractoKey} son los nombres de las partes
 * multipart donde viene el PDF correspondiente (ej. "factura_0", "extracto_0").
 * {@code extractoKey} es null cuando el proveedor no requiere extracto
 * (ej. Repsol).
 */
public record ImportarFacturaLoteItemDTO(
        Long proveedorId,
        String facturaKey,
        String extractoKey
) {}
