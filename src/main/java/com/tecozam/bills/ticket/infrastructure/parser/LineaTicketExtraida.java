package com.tecozam.bills.ticket.infrastructure.parser;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una línea extraída por {@link LlmTicketParser} de UNA de las N fotos que
 * componen un ticket. Mapea 1:1 a los campos que hoy ya extrae el OCR de
 * tickets (ver {@code TicketController.performOcr}), más {@code fotoIndice}
 * para saber de qué foto salió. El wiring a {@code TicketLinea} real es
 * tarea de otro turno (T3) — este record es deliberadamente independiente
 * de JPA.
 */
public record LineaTicketExtraida(
        int fotoIndice,
        String proveedor,
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
        String nifEstacion
) {
}
