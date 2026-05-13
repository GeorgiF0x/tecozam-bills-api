package com.tecozam.bills.ticket.dto;

import com.tecozam.bills.vehiculo.domain.CategoriaRecurso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request para crear un ticket validado con PIN tras OCR.
 *
 * Los campos OCR (estacion, fechaHora, importeTotal...) son OPCIONALES:
 * - Si vienen, se usan directamente sin re-llamar a OpenAI (flujo nuevo, el
 *   conductor ya editó los datos en cliente tras un preview).
 * - Si no vienen, el backend llama al OCR con la imagen como fallback
 *   (flujo legacy, compatibilidad con clientes antiguos).
 */
public record CreateTicketOcrValidadoRequest(
        @NotNull Long tarjetaId,
        @NotBlank @Pattern(regexp = "^\\d{4}$", message = "El PIN debe tener exactamente 4 dígitos numéricos") String pin,
        @NotNull CategoriaRecurso categoriaRecurso,
        @NotNull Long vehiculoId,
        @NotNull Long centroCosteId,
        Integer kilometros,
        // ── Datos OCR opcionales (modo "preview previo en cliente") ──────
        String estacion,
        LocalDateTime fechaHora,
        BigDecimal importeTotal,
        BigDecimal litros,
        BigDecimal precioLitro,
        String producto,
        String numRecibo,
        String matricula,
        String ocrRaw
) {}
