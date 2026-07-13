package com.tecozam.bills.tarifa.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record UpdateTarifaRequest(
    String codigoTarifa,
    @NotNull LocalDate vigenteDesde,
    LocalDate vigenteHasta,
    String observaciones,
    @NotNull List<CreateTarifaRequest.PrecioInput> precios
) {}
