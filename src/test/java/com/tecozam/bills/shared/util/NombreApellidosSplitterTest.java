package com.tecozam.bills.shared.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NombreApellidosSplitterTest {

    @Test
    void inputNuloDevuelveDosCadenasVacias() {
        assertThat(NombreApellidosSplitter.split(null)).containsExactly("", "");
    }

    @Test
    void inputVacioDevuelveDosCadenasVacias() {
        assertThat(NombreApellidosSplitter.split("   ")).containsExactly("", "");
    }

    @Test
    void unaSolaPalabraEsTodaNombre() {
        assertThat(NombreApellidosSplitter.split("JUAN")).containsExactly("JUAN", "");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "JUAN GARCIA            | JUAN     | GARCIA",
            "JUAN GARCIA LOPEZ      | JUAN     | GARCIA LOPEZ",
            "PEDRO MARTIN SOLDEVILLA| PEDRO    | MARTIN SOLDEVILLA"
    })
    void nombreSimpleSeparaPrimeraPalabraDelResto(String input, String nombre, String apellidos) {
        assertThat(NombreApellidosSplitter.split(input)).containsExactly(nombre, apellidos);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "JOSE LUIS SUAREZ COLLADO | JOSE LUIS    | SUAREZ COLLADO",
            "MARIA CARMEN GARCIA      | MARIA CARMEN | GARCIA",
            "MIGUEL ANGEL FERNANDEZ   | MIGUEL ANGEL | FERNANDEZ",
            "JUAN CARLOS GOMEZ PEREZ  | JUAN CARLOS  | GOMEZ PEREZ"
    })
    void nombresCompuestosSeMantienenJuntos(String input, String nombre, String apellidos) {
        assertThat(NombreApellidosSplitter.split(input)).containsExactly(nombre, apellidos);
    }

    @Test
    void aceptaInputConTildesYMinusculas() {
        // 'JOSÉ LUIS' debe reconocerse como compuesto (insensible a tildes y mayusculas)
        assertThat(NombreApellidosSplitter.split("José Luis García Pérez"))
                .containsExactly("José Luis", "García Pérez");
    }

    @Test
    void colapsaEspaciosMultiples() {
        assertThat(NombreApellidosSplitter.split("JUAN    GARCIA"))
                .containsExactly("JUAN", "GARCIA");
    }
}
