package com.tecozam.bills.proveedor.dto;

public record ProveedorDTO(
        Long id,
        String codigo,
        String nombre,
        String nif,
        boolean activo
) {}
