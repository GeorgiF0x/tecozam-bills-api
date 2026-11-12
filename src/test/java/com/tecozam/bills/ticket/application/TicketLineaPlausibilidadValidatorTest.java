package com.tecozam.bills.ticket.application;

import com.tecozam.bills.ticket.domain.TipoGasto;
import com.tecozam.bills.ticket.dto.LineaOcrDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketLineaPlausibilidadValidatorTest {

    private final TicketLineaPlausibilidadValidator validator = new TicketLineaPlausibilidadValidator();

    private LineaOcrDTO linea(String estacion, String matricula, BigDecimal litros, BigDecimal precioLitro,
                               BigDecimal importeTotal) {
        return new LineaOcrDTO(0, estacion, LocalDateTime.now(), "GASOLEO", null, matricula, null,
                litros, precioLitro, importeTotal);
    }

    @Test
    void lineaCoherenteNoGeneraAvisos() {
        List<String> avisos = validator.validar(List.of(
                linea("ESTACION A", "1234ABC", new BigDecimal("20.00"), new BigDecimal("1.75"), new BigDecimal("35.00"))),
                TipoGasto.COMBUSTIBLE);

        assertThat(avisos).isEmpty();
    }

    @Test
    void importeQueNoCuadraConLitrosPorPrecioGeneraAviso() {
        List<String> avisos = validator.validar(List.of(
                linea("ESTACION A", "1234ABC", new BigDecimal("20.00"), new BigDecimal("1.75"), new BigDecimal("90.00"))),
                TipoGasto.COMBUSTIBLE);

        assertThat(avisos).anyMatch(a -> a.contains("no cuadra con litros"));
    }

    @Test
    void precioLitroFueraDeRangoGeneraAviso() {
        List<String> avisos = validator.validar(List.of(
                linea("ESTACION A", "1234ABC", new BigDecimal("20.00"), new BigDecimal("9.99"), new BigDecimal("199.80"))),
                TipoGasto.COMBUSTIBLE);

        assertThat(avisos).anyMatch(a -> a.contains("fuera de rango plausible"));
    }

    @Test
    void peajeNoValidaPrecioLitroAunqueSeaCero() {
        List<String> avisos = validator.validar(List.of(
                linea("AP-68 SALIDA 12", null, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("3.30"))),
                TipoGasto.PEAJE);

        assertThat(avisos).isEmpty();
    }

    @Test
    void litrosAbsurdamenteAltosGeneraAviso() {
        List<String> avisos = validator.validar(List.of(
                linea("ESTACION A", "1234ABC", new BigDecimal("900.00"), new BigDecimal("1.75"), new BigDecimal("1575.00"))),
                TipoGasto.COMBUSTIBLE);

        assertThat(avisos).anyMatch(a -> a.contains("inusualmente alto"));
    }

    @Test
    void estacionesDistintasEntreLineasGeneraAviso() {
        List<String> avisos = validator.validar(List.of(
                linea("ESTACION A", "1234ABC", new BigDecimal("20.00"), new BigDecimal("1.75"), new BigDecimal("35.00")),
                linea("ESTACION B", "1234ABC", new BigDecimal("10.00"), new BigDecimal("1.75"), new BigDecimal("17.50"))),
                TipoGasto.COMBUSTIBLE);

        assertThat(avisos).anyMatch(a -> a.contains("estaciones distintas"));
    }

    @Test
    void matriculasDistintasEntreLineasGeneraAviso() {
        List<String> avisos = validator.validar(List.of(
                linea("ESTACION A", "1234ABC", new BigDecimal("20.00"), new BigDecimal("1.75"), new BigDecimal("35.00")),
                linea("ESTACION A", "9999ZZZ", new BigDecimal("10.00"), new BigDecimal("1.75"), new BigDecimal("17.50"))),
                TipoGasto.COMBUSTIBLE);

        assertThat(avisos).anyMatch(a -> a.contains("matriculas distintas"));
    }

    @Test
    void camposNulosNoLanzanExcepcion() {
        List<String> avisos = validator.validar(List.of(
                new LineaOcrDTO(0, null, null, null, null, null, null, null, null, null)),
                TipoGasto.COMBUSTIBLE);

        assertThat(avisos).isEmpty();
    }
}
