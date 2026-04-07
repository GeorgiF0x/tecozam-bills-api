package com.tecozam.bills.vehiculo.dto;

public record UpdateVehiculoRequest(
        String descripcion,
        String estado,
        Boolean activo
) {}
