package com.tecozam.bills.centrocoste.dto;

public record UpdateCentroCosteRequest(
        String nombre,
        String descripcion,
        Boolean activo
) {}
