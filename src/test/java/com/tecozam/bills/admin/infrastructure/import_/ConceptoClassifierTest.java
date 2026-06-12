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

    // ── NEW-03 ───────────────────────────────────────────────────────────────
    // Conceptos reales que aparecen en los Excel Cepsa/Moeve y Repsol que el
    // classifier debe reconocer como TARJETA o VIAT (no caer al default).

    @ParameterizedTest
    @ValueSource(strings = {
            "USO RED PORTUGAL", "USO RED ESPAÑA",
            "GEST. SERV. AUTOP. ESPAÑA",
            "CUOTA OBE/VIA-T",
            "TELEPEAJE"
    })
    @DisplayName("NEW-03: conceptos de telepeaje real → VIAT (sin caer a default)")
    void conceptosTelepeajeRealEsVIAT(String concepto) {
        assertThat(ConceptoClassifier.clasificar(concepto)).isEqualTo(TipoRecurso.VIAT);
        assertThat(ConceptoClassifier.esConceptoConocido(concepto)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // combustibles variantes
            "DIESEL E+1", "DIESEL E+5", "DIESEL E+10", "DIE E+",
            "DIESELNEXR", "EFITEC 95", "EFI 95", "EFI 98", "OPTIMA 98",
            "GNA SEM PB 95", "GSL 95", "DSL",
            // ECOBLUE/ADBLUE variantes
            "ECOBLUE 5 LT", "ECOBLUE GRANEL", "ECOBLUE GARRAFA",
            "ADBLUE EMB", "ADBLUEREPS", "ADB+GRN", "ADBLUE GRL",
            "BLUE+GRANE",
            // servicios/staff
            "PARKING", "LAVADO/ENGRASE", "ACEITES/LUBES",
            "LUBRICANTE", "LUBRIFICTE",
            "OUTRAS COMPRAS", "OTROS PROD", "OTR BOMGAS",
            "GEST. SERV. PARKING ESPAÑA",
            "SUBVENCIÓN GOBIERNO DE NAVARRA"
    })
    @DisplayName("NEW-03: conceptos reales del Excel cliente → TARJETA reconocido")
    void conceptosRealesClienteEsTarjetaReconocido(String concepto) {
        assertThat(ConceptoClassifier.clasificar(concepto)).isEqualTo(TipoRecurso.TARJETA);
        assertThat(ConceptoClassifier.esConceptoConocido(concepto)).isTrue();
    }
}
