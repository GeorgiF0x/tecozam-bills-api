package com.tecozam.bills.prestamo.dto;

/**
 * Representación ligera de un recurso (tarjeta, vehículo o viat) disponible
 * para préstamo. Devuelto por GET /api/prestamos/recursos-disponibles?tipo=X.
 */
public record RecursoDisponibleDTO(
        Long id,
        String descripcion,
        String detalle
) {}
