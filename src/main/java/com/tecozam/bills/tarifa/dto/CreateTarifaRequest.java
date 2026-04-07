package com.tecozam.bills.tarifa.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record CreateTarifaRequest(
    @NotNull Long proveedorId,
    String codigoTarifa,
    @NotNull LocalDate vigenteDesde,
    LocalDate vigenteHasta,
    String observaciones,
    @NotNull List<PrecioInput> precios
) {
    public record PrecioInput(
        String producto,
        String conceptoUnificado,
        java.math.BigDecimal precioSinIva,
        java.math.BigDecimal precioConIva
    ) {}
}
