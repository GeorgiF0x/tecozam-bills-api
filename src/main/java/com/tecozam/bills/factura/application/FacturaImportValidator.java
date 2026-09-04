package com.tecozam.bills.factura.application;

import com.tecozam.bills.factura.domain.Factura;
import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.domain.TarjetaResumen;
import com.tecozam.bills.shared.domain.enums.ConceptoUnificado;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Chequeos de plausibilidad sobre una factura recien parseada, ANTES de
 * persistirla. No sustituyen al parser ni bloquean la importacion: su unico
 * trabajo es detectar cuando un formato de factura no visto antes ha hecho
 * que el parser lea mal un campo en silencio (el mismo patron que causo el
 * bug real de litros duplicados en RepsolFacturaParser/MoeveFacturaParser),
 * para que un humano lo revise en vez de que el dato basura entre al cotejo
 * sin que nadie se entere.
 */
@Component
public class FacturaImportValidator {

    private static final Set<String> CONCEPTOS_COMBUSTIBLE = Set.of(
            ConceptoUnificado.DIESEL.name(),
            ConceptoUnificado.GASOLINA.name(),
            ConceptoUnificado.ADBLUE.name());

    private static final BigDecimal PRECIO_LITRO_MIN = new BigDecimal("0.50");
    private static final BigDecimal PRECIO_LITRO_MAX = new BigDecimal("3.50");
    private static final BigDecimal CANTIDAD_MAX_REPOSTAJE = new BigDecimal("500");
    private static final double TOLERANCIA_IMPORTE_CALCULADO = 0.15; // 15%
    private static final int DIAS_GRACIA_PERIODO = 7;

    public List<String> validar(Factura factura) {
        List<String> avisos = new ArrayList<>();

        for (TarjetaResumen tr : factura.getTarjetaResumenes()) {
            for (Operacion op : tr.getOperaciones()) {
                validarOperacion(factura, tr, op, avisos);
            }
        }

        return avisos;
    }

    private void validarOperacion(Factura factura, TarjetaResumen tr, Operacion op, List<String> avisos) {
        String ref = referencia(tr, op);
        boolean esCombustible = op.getConceptoUnificado() != null
                && CONCEPTOS_COMBUSTIBLE.contains(op.getConceptoUnificado());

        // 0. Cantidad presente en un concepto que no es de combustible real.
        // Este es el chequeo mas directo contra el bug real ya corregido: para
        // TIENDA/PEAJE/LAVADO/LUBRICANTE/DESCUENTO/OTROS no existe una
        // "cantidad" real en la factura, y si aparece un valor no nulo lo mas
        // probable es que el parser haya vuelto a guardar el importe
        // duplicado como si fueran litros (el mismo patron corregido en
        // RepsolFacturaParser y MoeveFacturaParser via el guard
        // tieneLitrosReales). No depende de que la magnitud parezca "absurda".
        if (!esCombustible && op.getCantidad() != null && op.getCantidad().signum() != 0) {
            avisos.add(String.format(Locale.ROOT,
                    "%s: tiene cantidad=%.2f pero este concepto no deberia tener litros/cantidad real. Posible importe duplicado como cantidad.",
                    ref, op.getCantidad().doubleValue()));
        }

        // 1. Precio por litro fuera de rango plausible (fuera de esto, casi
        // seguro que el parser confundio una columna).
        BigDecimal precio = op.getPrecioUnitario() != null ? op.getPrecioUnitario() : op.getPrecioIvaInc();
        if (esCombustible && precio != null
                && (precio.compareTo(PRECIO_LITRO_MIN) < 0 || precio.compareTo(PRECIO_LITRO_MAX) > 0)) {
            avisos.add(String.format(Locale.ROOT,
                    "%s: precio/litro %.3f€ fuera de rango plausible (%.2f–%.2f€). Revisar si el parser leyo bien la columna de precio.",
                    ref, precio.doubleValue(), PRECIO_LITRO_MIN.doubleValue(), PRECIO_LITRO_MAX.doubleValue()));
        }

        // 2. Cantidad de un solo repostaje absurdamente alta — senal de que el
        // parser guardo un importe (o una cantidad acumulada) como si fueran
        // litros de una operacion individual.
        if (esCombustible && op.getCantidad() != null
                && op.getCantidad().compareTo(CANTIDAD_MAX_REPOSTAJE) > 0) {
            avisos.add(String.format(Locale.ROOT,
                    "%s: %.2f L en un solo repostaje es inusualmente alto (>%.0f L). Revisar si el valor guardado como cantidad es realmente litros.",
                    ref, op.getCantidad().doubleValue(), CANTIDAD_MAX_REPOSTAJE.doubleValue()));
        }

        // 3. importeTotal no cuadra con cantidad x precioUnitario — indica que
        // alguno de los tres campos se leyo de la columna equivocada.
        if (op.getImporteTotal() != null && op.getCantidad() != null && precio != null
                && op.getCantidad().signum() > 0 && precio.signum() > 0) {
            BigDecimal calculado = op.getCantidad().multiply(precio);
            double diffPct = calculado.signum() != 0
                    ? Math.abs(op.getImporteTotal().doubleValue() - calculado.doubleValue()) / calculado.doubleValue()
                    : 0;
            if (diffPct > TOLERANCIA_IMPORTE_CALCULADO) {
                avisos.add(String.format(Locale.ROOT,
                        "%s: importe %.2f€ no cuadra con cantidad x precio (%.2f€ calculado, %.0f%% de diferencia).",
                        ref, op.getImporteTotal().doubleValue(), calculado.doubleValue(), diffPct * 100));
            }
        }

        // 4. Importe negativo fuera de un concepto de descuento — posible
        // signo mal interpretado.
        if (op.getImporteTotal() != null && op.getImporteTotal().signum() < 0
                && !ConceptoUnificado.DESCUENTO.name().equals(op.getConceptoUnificado())) {
            avisos.add(String.format(Locale.ROOT,
                    "%s: importe negativo (%.2f€) en un concepto que no es descuento.",
                    ref, op.getImporteTotal().doubleValue()));
        }

        // 5. Fecha de la operacion fuera del periodo de facturacion (con
        // margen de gracia) — senal de que se parseo mal una fecha.
        LocalDateTime fecha = op.getFechaHora();
        LocalDate desde = factura.getPeriodoDesde();
        LocalDate hasta = factura.getPeriodoHasta();
        if (fecha != null && desde != null && hasta != null) {
            LocalDate fechaOp = fecha.toLocalDate();
            LocalDate margenDesde = desde.minusDays(DIAS_GRACIA_PERIODO);
            LocalDate margenHasta = hasta.plusDays(DIAS_GRACIA_PERIODO);
            if (fechaOp.isBefore(margenDesde) || fechaOp.isAfter(margenHasta)) {
                avisos.add(String.format(Locale.ROOT,
                        "%s: fecha %s fuera del periodo de la factura (%s a %s).",
                        ref, fechaOp, desde, hasta));
            }
        }
    }

    private String referencia(TarjetaResumen tr, Operacion op) {
        String tarjeta = tr.getNumTarjeta() != null ? tr.getNumTarjeta() : "tarjeta desconocida";
        String concepto = op.getConceptoUnificado() != null ? op.getConceptoUnificado() : "concepto desconocido";
        String fecha = op.getFechaHora() != null ? op.getFechaHora().toString() : "fecha desconocida";
        return String.format("[%s | %s | %s]", tarjeta, concepto, fecha);
    }
}
