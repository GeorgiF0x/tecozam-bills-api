package com.tecozam.bills.ticket.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TicketDTO(
        Long id,
        String origen,
        Long proveedorId,
        String proveedorNombre,
        Long trabajadorId,
        String trabajadorNombre,
        Long tarjetaId,
        Long vehiculoId,
        String estacion,
        String direccion,
        LocalDateTime fechaHora,
        String numTarjeta4ultimos,
        String matricula,
        Integer kms,
        String producto,
        BigDecimal litros,
        BigDecimal precioLitro,
        BigDecimal importeTotal,
        String numRecibo,
        String nifEstacion,
        String imagenUrl,
        String concepto,
        String observaciones,
        String estadoCotejo,
        Long operacionCotejadaId,
        String tipoIncidencia,
        Long asignadoAId,
        String asignadoANombre,
        String notasResolucion,
        LocalDateTime resueltoEn,
        LocalDateTime creadoEn
) {}
