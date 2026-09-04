package com.tecozam.bills.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateTicketManualRequest(
        Long proveedorId,
        Long trabajadorId,
        Long tarjetaId,
        Long vehiculoId,
        @NotBlank String estacion,
        @NotNull LocalDateTime fechaHora,
        @NotNull @Positive BigDecimal importeTotal,
        BigDecimal litros,
        BigDecimal precioLitro,
        Integer kms,
        String concepto,
        String observaciones,
        @Pattern(regexp = "\\d{4}", message = "Los últimos 4 dígitos de tarjeta deben ser exactamente 4 números") String numTarjeta4ultimos,
        @Size(max = 20, message = "La matrícula no puede superar 20 caracteres") String matricula,
        @Size(max = 20, message = "El NIF de estación no puede superar 20 caracteres") String nifEstacion,
        @Size(max = 250, message = "La dirección no puede superar 250 caracteres") String direccion
) {}
