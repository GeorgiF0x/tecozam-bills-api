package com.tecozam.bills.admin.infrastructure.import_;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListadoTarjetasParserFactoryTest {

    private final RepsolXlsxRowParser repsolParser = new RepsolXlsxRowParser();
    private final CepsaXlsxRowParser cepsaParser = new CepsaXlsxRowParser();
    private final ListadoTarjetasParserFactory factory =
            new ListadoTarjetasParserFactory(repsolParser, cepsaParser);

    @ParameterizedTest
    @ValueSource(strings = {"REPSOL", "SOLRED", "repsol", "Solred"})
    @DisplayName("Alias Repsol/Solred (case-insensitive) → RepsolXlsxRowParser")
    void aliasRepsolDevuelveRepsolParser(String codigo) {
        assertThat(factory.parserPara(codigo)).isSameAs(repsolParser);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CEPSA", "MOEVE", "MOEVE_CEPSA", "cepsa", "Moeve"})
    @DisplayName("Alias Cepsa/Moeve (case-insensitive) → CepsaXlsxRowParser")
    void aliasCepsaDevuelveCepsaParser(String codigo) {
        assertThat(factory.parserPara(codigo)).isSameAs(cepsaParser);
    }

    @Test
    @DisplayName("Proveedor no soportado lanza IllegalArgumentException con el código en el mensaje")
    void proveedorNoSoportadoLanzaExcepcion() {
        assertThatThrownBy(() -> factory.parserPara("GALP"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GALP");
    }

    @Test
    @DisplayName("Proveedor null lanza IllegalArgumentException")
    void proveedorNullLanzaExcepcion() {
        assertThatThrownBy(() -> factory.parserPara(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
