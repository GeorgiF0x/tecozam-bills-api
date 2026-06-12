package com.tecozam.bills.tarjeta.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TarjetaNumeroNormalizerTest {

    @Test
    @DisplayName("Repsol con prefijo 0007 (19 chars) → canónico 15 chars sin prefijo")
    void repsolConPrefijoDevuelveCanonicoSinPrefijo() {
        String canonical = TarjetaNumeroNormalizer.canonical("0007078833651671188", "REPSOL");
        assertThat(canonical).isEqualTo("078833651671188");
        assertThat(canonical).hasSize(15);
    }

    @Test
    @DisplayName("Repsol ya canónico (15 chars) → idempotente")
    void repsolCanonicoEsIdempotente() {
        String canonical = TarjetaNumeroNormalizer.canonical("078833651671188", "REPSOL");
        assertThat(canonical).isEqualTo("078833651671188");
    }

    @Test
    @DisplayName("Cepsa tarjeta 18 chars (prefijo 70801) → intacto")
    void cepsaTarjetaQuedaIntacta() {
        String canonical = TarjetaNumeroNormalizer.canonical("708011008022409211", "CEPSA");
        assertThat(canonical).isEqualTo("708011008022409211");
    }

    @Test
    @DisplayName("Cepsa VIAT 16 chars (prefijo 70764) → intacto")
    void cepsaViatQuedaIntacto() {
        String canonical = TarjetaNumeroNormalizer.canonical("7076460769901026", "CEPSA");
        assertThat(canonical).isEqualTo("7076460769901026");
    }

    @Test
    @DisplayName("MOEVE es alias de CEPSA")
    void moeveEsAliasDeCepsa() {
        String canonical = TarjetaNumeroNormalizer.canonical("708011008022409211", "MOEVE");
        assertThat(canonical).isEqualTo("708011008022409211");
    }

    @Test
    @DisplayName("SOLRED es alias de REPSOL")
    void solredEsAliasDeRepsol() {
        String canonical = TarjetaNumeroNormalizer.canonical("0007078833651671188", "SOLRED");
        assertThat(canonical).isEqualTo("078833651671188");
    }

    @Test
    @DisplayName("Espacios en blanco se eliminan antes de normalizar")
    void espaciosEnBlancoSeEliminan() {
        String canonical = TarjetaNumeroNormalizer.canonical("  0007 078 833 651 671 188  ", "REPSOL");
        assertThat(canonical).isEqualTo("078833651671188");
    }

    @Test
    @DisplayName("Número null lanza IllegalArgumentException")
    void numeroNullLanzaExcepcion() {
        assertThatThrownBy(() -> TarjetaNumeroNormalizer.canonical(null, "REPSOL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("número");
    }

    @Test
    @DisplayName("Número en blanco lanza IllegalArgumentException")
    void numeroBlancoLanzaExcepcion() {
        assertThatThrownBy(() -> TarjetaNumeroNormalizer.canonical("   ", "REPSOL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("número");
    }

    @Test
    @DisplayName("Proveedor null lanza IllegalArgumentException")
    void proveedorNullLanzaExcepcion() {
        assertThatThrownBy(() -> TarjetaNumeroNormalizer.canonical("078833651671188", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("proveedor");
    }

    @Test
    @DisplayName("Proveedor desconocido lanza IllegalArgumentException")
    void proveedorDesconocidoLanzaExcepcion() {
        assertThatThrownBy(() -> TarjetaNumeroNormalizer.canonical("078833651671188", "GALP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GALP");
    }

    @Test
    @DisplayName("Proveedor case-insensitive: repsol minúsculas equivale a REPSOL")
    void proveedorEsCaseInsensitive() {
        String canonical = TarjetaNumeroNormalizer.canonical("0007078833651671188", "repsol");
        assertThat(canonical).isEqualTo("078833651671188");
    }
}
