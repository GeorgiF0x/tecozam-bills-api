package com.tecozam.bills.admin.infrastructure.import_;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No llama a la API real de OpenAI (sin red, sin gastar tokens): prueba
 * únicamente el mapeo JSON → {@link FilaImportada} ({@link LlmListadoTarjetasParser#construirFilas})
 * con una respuesta de ejemplo fija — mismo patrón que
 * {@code factura.infrastructure.parser.LlmFacturaParserTest}.
 */
class LlmListadoTarjetasParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmListadoTarjetasParser parser = new LlmListadoTarjetasParser();

    @Test
    @DisplayName("mapea una respuesta JSON con filas TARJETA y VIAT a FilaImportada")
    void construirFilas_mapeaTodosLosCampos() throws Exception {
        String json = """
                {
                  "filas": [
                    {
                      "numero": "0007078833651671188",
                      "matricula": "1234-ABC",
                      "nombreCompleto": "Juan Perez",
                      "centroCoste": "OBRA-1",
                      "concepto": "DIESEL E+",
                      "tipoSugerido": "TARJETA"
                    },
                    {
                      "numero": "708011008022415028",
                      "matricula": null,
                      "nombreCompleto": null,
                      "centroCoste": "OBRA-2",
                      "concepto": "AUTOPISTAS DE PEAJE",
                      "tipoSugerido": "VIAT"
                    }
                  ]
                }
                """;

        JsonNode raiz = mapper.readTree(json);
        List<FilaImportada> filas = parser.construirFilas(raiz);

        assertThat(filas).hasSize(2);

        FilaImportada tarjeta = filas.get(0);
        assertThat(tarjeta.numero()).isEqualTo("0007078833651671188");
        assertThat(tarjeta.matricula()).isEqualTo("1234-ABC");
        assertThat(tarjeta.concepto()).isEqualTo("DIESEL E+");
        assertThat(tarjeta.tipo()).isEqualTo(TipoRecurso.TARJETA);
        assertThat(tarjeta.conceptoConocido()).isTrue();

        FilaImportada viat = filas.get(1);
        assertThat(viat.numero()).isEqualTo("708011008022415028");
        assertThat(viat.matricula()).isNull();
        assertThat(viat.tipo()).isEqualTo(TipoRecurso.VIAT);
    }

    @Test
    @DisplayName("filas sin numero se descartan (fila vacia o de separacion)")
    void construirFilas_sinNumero_seDescarta() throws Exception {
        String json = """
                {
                  "filas": [
                    { "numero": "", "matricula": null, "nombreCompleto": null,
                      "centroCoste": null, "concepto": "DIESEL", "tipoSugerido": "TARJETA" },
                    { "numero": "123", "matricula": null, "nombreCompleto": null,
                      "centroCoste": null, "concepto": "DIESEL", "tipoSugerido": "TARJETA" }
                  ]
                }
                """;

        JsonNode raiz = mapper.readTree(json);
        List<FilaImportada> filas = parser.construirFilas(raiz);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).numero()).isEqualTo("123");
    }
}
