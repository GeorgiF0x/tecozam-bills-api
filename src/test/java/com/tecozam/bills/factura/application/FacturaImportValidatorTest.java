package com.tecozam.bills.factura.application;

import com.tecozam.bills.factura.domain.Factura;
import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.domain.TarjetaResumen;
import com.tecozam.bills.shared.domain.enums.ConceptoUnificado;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FacturaImportValidatorTest {

    private final FacturaImportValidator validator = new FacturaImportValidator();

    @Test
    void operacionPlausible_noGeneraAvisos() {
        Factura factura = facturaConPeriodo(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        TarjetaResumen tr = tarjetaResumen(factura, "1234");
        tr.getOperaciones().add(operacion(tr, factura,
                LocalDateTime.of(2026, 7, 15, 10, 0), ConceptoUnificado.DIESEL,
                "44.90", "1.648", "74.00"));

        List<String> avisos = validator.validar(factura);

        assertThat(avisos).isEmpty();
    }

    @Test
    void cantidadEnConceptoSinLitrosReales_generaAviso_replicaBugRealDeTienda() {
        // Replica exacta del bug real ya corregido en RepsolFacturaParser: una
        // linea de TIENDA (61,96 61,96 en el PDF) donde el importe se leia
        // dos veces y la primera ocurrencia se guardaba como "cantidad".
        Factura factura = facturaConPeriodo(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        TarjetaResumen tr = tarjetaResumen(factura, "1234");
        tr.getOperaciones().add(operacion(tr, factura,
                LocalDateTime.of(2026, 7, 15, 10, 0), ConceptoUnificado.OTROS,
                "61.96", null, "61.96"));

        List<String> avisos = validator.validar(factura);

        assertThat(avisos).anyMatch(a -> a.contains("no deberia tener litros"));
    }

    @Test
    void precioPorLitroFueraDeRango_generaAviso() {
        Factura factura = facturaConPeriodo(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        TarjetaResumen tr = tarjetaResumen(factura, "1234");
        // precio/litro de 12,02€ — imposible para diesel, tipico de un
        // importe de tienda leido en la columna de precio.
        tr.getOperaciones().add(operacion(tr, factura,
                LocalDateTime.of(2026, 7, 15, 10, 0), ConceptoUnificado.DIESEL,
                "1", "12.02", "12.02"));

        List<String> avisos = validator.validar(factura);

        assertThat(avisos).anyMatch(a -> a.contains("precio/litro"));
    }

    @Test
    void cantidadRepostajeAbsurdamenteAlta_generaAviso() {
        Factura factura = facturaConPeriodo(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        TarjetaResumen tr = tarjetaResumen(factura, "1234");
        // Mismo patron real del bug: importe de tienda (61,96) guardado como
        // si fueran 61,96 litros en un repostaje.
        tr.getOperaciones().add(operacion(tr, factura,
                LocalDateTime.of(2026, 7, 15, 10, 0), ConceptoUnificado.DIESEL,
                "619.60", "1.648", "1020.68"));

        List<String> avisos = validator.validar(factura);

        assertThat(avisos).anyMatch(a -> a.contains("repostaje"));
    }

    @Test
    void importeNoCuadraConCantidadPorPrecio_generaAviso() {
        Factura factura = facturaConPeriodo(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        TarjetaResumen tr = tarjetaResumen(factura, "1234");
        tr.getOperaciones().add(operacion(tr, factura,
                LocalDateTime.of(2026, 7, 15, 10, 0), ConceptoUnificado.DIESEL,
                "44.90", "1.648", "500.00"));

        List<String> avisos = validator.validar(factura);

        assertThat(avisos).anyMatch(a -> a.contains("no cuadra"));
    }

    @Test
    void fechaFueraDelPeriodoDeFactura_generaAviso() {
        Factura factura = facturaConPeriodo(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        TarjetaResumen tr = tarjetaResumen(factura, "1234");
        tr.getOperaciones().add(operacion(tr, factura,
                LocalDateTime.of(2026, 1, 1, 10, 0), ConceptoUnificado.DIESEL,
                "44.90", "1.648", "74.00"));

        List<String> avisos = validator.validar(factura);

        assertThat(avisos).anyMatch(a -> a.contains("fuera del periodo"));
    }

    @Test
    void descuentoNegativo_noGeneraAvisoDeSigno() {
        Factura factura = facturaConPeriodo(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
        TarjetaResumen tr = tarjetaResumen(factura, "1234");
        Operacion descuento = operacion(tr, factura,
                LocalDateTime.of(2026, 7, 15, 10, 0), ConceptoUnificado.DESCUENTO,
                null, null, "-5.00");
        tr.getOperaciones().add(descuento);

        List<String> avisos = validator.validar(factura);

        assertThat(avisos).noneMatch(a -> a.contains("importe negativo"));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private Factura facturaConPeriodo(LocalDate desde, LocalDate hasta) {
        Factura factura = Factura.builder()
                .periodoDesde(desde)
                .periodoHasta(hasta)
                .build();
        return factura;
    }

    private TarjetaResumen tarjetaResumen(Factura factura, String numTarjeta) {
        TarjetaResumen tr = TarjetaResumen.builder()
                .factura(factura)
                .numTarjeta(numTarjeta)
                .build();
        factura.getTarjetaResumenes().add(tr);
        return tr;
    }

    private Operacion operacion(TarjetaResumen tr, Factura factura, LocalDateTime fecha,
            ConceptoUnificado concepto, String cantidad, String precioUnitario, String importeTotal) {
        return Operacion.builder()
                .tarjetaResumen(tr)
                .factura(factura)
                .fechaHora(fecha)
                .conceptoUnificado(concepto.name())
                .cantidad(cantidad != null ? new BigDecimal(cantidad) : null)
                .precioUnitario(precioUnitario != null ? new BigDecimal(precioUnitario) : null)
                .importeTotal(new BigDecimal(importeTotal))
                .build();
    }
}
