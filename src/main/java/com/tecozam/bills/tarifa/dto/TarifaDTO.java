package com.tecozam.bills.tarifa.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TarifaDTO(
    Long id,
    Long proveedorId,
    String proveedorNombre,
    String codigoTarifa,
    LocalDate vigenteDesde,
    LocalDate vigenteHasta,
    String observaciones,
    List<TarifaPrecioDTO> precios,
    LocalDateTime creadoEn
) {}
