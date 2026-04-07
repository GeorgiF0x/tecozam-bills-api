package com.tecozam.bills.factura.dto;

public record ImportarFacturaResponse(
        Long facturaId,
        String numFactura,
        String proveedor,
        int tarjetasImportadas,
        int operacionesImportadas,
        String rutaPdf,
        String mensaje
) {}
