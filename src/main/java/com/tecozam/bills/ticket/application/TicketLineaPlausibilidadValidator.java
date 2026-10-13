package com.tecozam.bills.ticket.application;

import com.tecozam.bills.ticket.dto.LineaOcrDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chequeos de plausibilidad sobre las lineas OCR de un ticket (una por foto),
 * ANTES de persistirlas. Mismo espiritu que
 * {@link com.tecozam.bills.factura.application.FacturaImportValidator}: no
 * bloquean la creacion, solo marcan {@code Ticket.requiereRevisionManual}
 * para que un humano lo revise si el OCR probablemente leyo mal algo.
 *
 * A diferencia de una factura, un ticket no tiene un total de cabecera
 * separado de sus lineas que comparar — en su lugar se valida la coherencia
 * aritmetica de cada linea y, sobre todo, que las lineas de un mismo ticket
 * (varias fotos) sean de verdad la MISMA compra (misma estacion/matricula),
 * que es el riesgo especifico de esta feature.
 */
@Component
public class TicketLineaPlausibilidadValidator {

    private static final BigDecimal PRECIO_LITRO_MIN = new BigDecimal("0.50");
    private static final BigDecimal PRECIO_LITRO_MAX = new BigDecimal("3.50");
    private static final BigDecimal LITROS_MAX_REPOSTAJE = new BigDecimal("500");
    private static final double TOLERANCIA_IMPORTE_CALCULADO = 0.15; // 15%, igual que FacturaImportValidator

    public List<String> validar(List<LineaOcrDTO> lineas) {
        List<String> avisos = new ArrayList<>();

        for (int i = 0; i < lineas.size(); i++) {
            validarLinea(i, lineas.get(i), avisos);
        }

        validarConsistenciaEntreLineas(lineas, avisos);

        return avisos;
    }

    private void validarLinea(int indice, LineaOcrDTO l, List<String> avisos) {
        String ref = String.format(Locale.ROOT, "Linea %d (foto %s)", indice + 1,
                l.fotoIndice() != null ? l.fotoIndice() : "?");

        if (l.precioLitro() != null
                && (l.precioLitro().compareTo(PRECIO_LITRO_MIN) < 0 || l.precioLitro().compareTo(PRECIO_LITRO_MAX) > 0)) {
            avisos.add(String.format(Locale.ROOT,
                    "%s: precio/litro %.3f€ fuera de rango plausible (%.2f–%.2f€).",
                    ref, l.precioLitro().doubleValue(), PRECIO_LITRO_MIN.doubleValue(), PRECIO_LITRO_MAX.doubleValue()));
        }

        if (l.litros() != null && l.litros().compareTo(LITROS_MAX_REPOSTAJE) > 0) {
            avisos.add(String.format(Locale.ROOT,
                    "%s: %.2f L es inusualmente alto para un solo repostaje (>%.0f L).",
                    ref, l.litros().doubleValue(), LITROS_MAX_REPOSTAJE.doubleValue()));
        }

        if (l.litros() != null && l.precioLitro() != null && l.importeTotal() != null
                && l.litros().signum() > 0 && l.precioLitro().signum() > 0) {
            BigDecimal calculado = l.litros().multiply(l.precioLitro());
            double diffPct = Math.abs(l.importeTotal().doubleValue() - calculado.doubleValue())
                    / calculado.doubleValue();
            if (diffPct > TOLERANCIA_IMPORTE_CALCULADO) {
                avisos.add(String.format(Locale.ROOT,
                        "%s: importe %.2f€ no cuadra con litros x precio/litro (%.2f€ calculado, %.0f%% de diferencia).",
                        ref, l.importeTotal().doubleValue(), calculado.doubleValue(), diffPct * 100));
            }
        }
    }

    /**
     * Senal especifica de esta feature: si varias lineas traen estacion o
     * matricula y difieren entre si, probablemente el usuario mezclo fotos
     * de dos compras distintas en un mismo envio.
     */
    private void validarConsistenciaEntreLineas(List<LineaOcrDTO> lineas, List<String> avisos) {
        long estacionesDistintas = lineas.stream()
                .map(LineaOcrDTO::estacion)
                .filter(e -> e != null && !e.isBlank())
                .map(e -> e.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .count();
        if (estacionesDistintas > 1) {
            avisos.add("Las lineas traen estaciones distintas — posible mezcla de fotos de compras diferentes en un mismo envio.");
        }

        long matriculasDistintas = lineas.stream()
                .map(LineaOcrDTO::matricula)
                .filter(m -> m != null && !m.isBlank())
                .map(m -> m.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .count();
        if (matriculasDistintas > 1) {
            avisos.add("Las lineas traen matriculas distintas — posible mezcla de fotos de compras diferentes en un mismo envio.");
        }
    }
}
