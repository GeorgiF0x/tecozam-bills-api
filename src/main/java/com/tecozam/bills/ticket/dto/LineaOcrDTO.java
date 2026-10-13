package com.tecozam.bills.ticket.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una línea de ticket, tal y como viaja entre cliente y servidor: como
 * resultado del OCR en el preview ({@code /ocr-preview}), y como línea ya
 * editada por el conductor al confirmar ({@code /ocr-validado}). Mapea 1:1
 * a los campos persistidos en {@code TicketLinea} (más {@code fotoIndice},
 * que solo existe para relacionar la línea con su foto de origen).
 */
public record LineaOcrDTO(
        Integer fotoIndice,
        String estacion,
        LocalDateTime fechaHora,
        String producto,
        String numRecibo,
        String matricula,
        Integer kms,
        BigDecimal litros,
        BigDecimal precioLitro,
        BigDecimal importeTotal
) {}
