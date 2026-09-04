package com.tecozam.bills.factura.dto;

import java.util.List;

public record ImportarFacturaResponse(
        Long facturaId,
        String numFactura,
        String proveedor,
        int tarjetasImportadas,
        int operacionesImportadas,
        String rutaPdf,
        String mensaje,
        List<String> avisos
) {}
