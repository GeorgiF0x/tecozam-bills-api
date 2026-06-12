package com.tecozam.bills.admin.infrastructure.import_;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ConceptoClassifierTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "GASOLEO", "DIESEL STAR", "ECOBLUE", "SIN PLOMO",
            "OPTIMA 95", "DIESEL E+", "ADBLUE", "STAFF", "TIENDA"
    })
    @DisplayName("Conceptos de combustible y compras → TARJETA")
    void combustibleEsTARJETA(String concepto) {
        assertThat(ConceptoClassifier.clasificar(concepto)).isEqualTo(TipoRecurso.TARJETA);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "AUTOPISTAS DE PEAJE", "TUNELES DE PEAJE",
            "PORTAGEM", "AUTOPISTAS", "PEAJE"
    })
    @DisplayName("Conceptos con keywords de peaje/autopista/túnel/portagem → VIAT")
    void peajesSonVIAT(String concepto) {
        assertThat(ConceptoClassifier.clasificar(concepto)).isEqualTo(TipoRecurso.VIAT);
    }

    @Test
    @DisplayName("Clasificación es case-insensitive")
    void clasificacionCaseInsensitive() {
        assertThat(ConceptoClassifier.clasificar("autopistas de peaje")).isEqualTo(TipoRecurso.VIAT);
        assertThat(ConceptoClassifier.clasificar("gasoleo")).isEqualTo(TipoRecurso.TARJETA);
    }

    @Test
    @DisplayName("Concepto null → TARJETA (default seguro)")
    void nullEsTarjetaPorDefecto() {
        assertThat(ConceptoClassifier.clasificar(null)).isEqualTo(TipoRecurso.TARJETA);
    }

    @Test
    @DisplayName("Concepto vacío → TARJETA (default seguro)")
    void vacioEsTarjetaPorDefecto() {
        assertThat(ConceptoClassifier.clasificar("")).isEqualTo(TipoRecurso.TARJETA);
        assertThat(ConceptoClassifier.clasificar("   ")).isEqualTo(TipoRecurso.TARJETA);
    }

    @Test
    @DisplayName("Concepto desconocido fuera del set de combustibles → TARJETA + flag")
    void desconocidoEsTarjetaPeroSeReporta() {
        assertThat(ConceptoClassifier.clasificar("XYZ-CONCEPTO-RARO")).isEqualTo(TipoRecurso.TARJETA);
        assertThat(ConceptoClassifier.esConceptoConocido("XYZ-CONCEPTO-RARO")).isFalse();
        assertThat(ConceptoClassifier.esConceptoConocido("GASOLEO")).isTrue();
        assertThat(ConceptoClassifier.esConceptoConocido("AUTOPISTAS DE PEAJE")).isTrue();
    }
}
