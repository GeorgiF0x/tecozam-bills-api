package com.tecozam.bills.admin.infrastructure.import_;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ConceptoRecognizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "GASOLEO", "DIESEL STAR", "ECOBLUE", "SIN PLOMO",
            "OPTIMA 95", "DIESEL E+", "ADBLUE", "STAFF", "TIENDA"
    })
    @DisplayName("Conceptos de combustible y compras se reconocen")
    void combustibleEsConocido(String concepto) {
        assertThat(ConceptoRecognizer.esConceptoConocido(concepto)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "AUTOPISTAS DE PEAJE", "TUNELES DE PEAJE",
            "PORTAGEM", "AUTOPISTAS", "PEAJE"
    })
    @DisplayName("Conceptos con keywords de peaje/autopista/túnel/portagem se reconocen")
    void peajesSeReconocen(String concepto) {
        assertThat(ConceptoRecognizer.esConceptoConocido(concepto)).isTrue();
    }

    @Test
    @DisplayName("Reconocimiento es case-insensitive")
    void reconocimientoCaseInsensitive() {
        assertThat(ConceptoRecognizer.esConceptoConocido("autopistas de peaje")).isTrue();
        assertThat(ConceptoRecognizer.esConceptoConocido("gasoleo")).isTrue();
    }

    @Test
    @DisplayName("Concepto null o vacío no se reconoce")
    void nullOVacioNoSeReconoce() {
        assertThat(ConceptoRecognizer.esConceptoConocido(null)).isFalse();
        assertThat(ConceptoRecognizer.esConceptoConocido("")).isFalse();
        assertThat(ConceptoRecognizer.esConceptoConocido("   ")).isFalse();
    }

    @Test
    @DisplayName("Concepto fuera de ambos sets no se reconoce")
    void desconocidoNoSeReconoce() {
        assertThat(ConceptoRecognizer.esConceptoConocido("XYZ-CONCEPTO-RARO")).isFalse();
    }

    // ── NEW-03 ───────────────────────────────────────────────────────────────
    // Conceptos reales que aparecen en los Excel Cepsa/Moeve y Repsol que el
    // reconocedor debe reconocer (no caer al "desconocido" y contarse en
    // filasIgnoradas de forma innecesaria).

    @ParameterizedTest
    @ValueSource(strings = {
            "USO RED PORTUGAL", "USO RED ESPAÑA",
            "GEST. SERV. AUTOP. ESPAÑA",
            "CUOTA OBE/VIA-T",
            "TELEPEAJE"
    })
    @DisplayName("NEW-03: conceptos de telepeaje real se reconocen")
    void conceptosTelepeajeRealSeReconocen(String concepto) {
        assertThat(ConceptoRecognizer.esConceptoConocido(concepto)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // combustibles variantes (incluyendo formato con puntos del Excel real)
            "DIESEL E+1", "DIESEL E+5", "DIESEL E+10", "DIE E+",
            "DIESELNEXR", "EFITEC 95", "EFI 95", "EFI 98", "OPTIMA 98",
            "GNA SEM PB 95", "GNA. SEM PB 95",
            "GSL 95", "DSL",
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
    @DisplayName("NEW-03: conceptos reales del Excel cliente se reconocen")
    void conceptosRealesClienteSeReconocen(String concepto) {
        assertThat(ConceptoRecognizer.esConceptoConocido(concepto)).isTrue();
    }
}
