package com.tecozam.bills.ticket.infrastructure.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No llama a la API real de OpenAI (sin red, sin gastar tokens): prueba
 * únicamente el mapeo JSON → {@link LineaTicketExtraida}
 * ({@link LlmTicketParser#construirResultado}), igual que
 * {@code LlmFacturaParserTest} hace con facturas — la llamada HTTP en sí
 * reutiliza el mismo patrón ya probado en producción.
 */
class LlmTicketParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmTicketParser parser = new LlmTicketParser("fake-key", "gpt-4.1-mini");

    @Test
    @DisplayName("mapea varias lineas de varias fotos a LineaTicketExtraida")
    void construirResultado_mapeaVariasLineasDeVariasFotos() throws Exception {
        String json = """
                {
                  "lineas": [
                    {
                      "fotoIndice": 0,
                      "proveedor": "REPSOL",
                      "estacion": "E.S. EJEMPLO",
                      "direccion": "Calle Falsa 123",
                      "fecha": "2026-09-15",
                      "hora": "10:30",
                      "numTarjeta4ultimos": "1234",
                      "matricula": "1234-ABC",
                      "kms": 50000,
                      "producto": "DIESEL",
                      "litros": 45.20,
                      "precioLitro": 1.648,
                      "importeTotal": 74.51,
                      "numRecibo": "REC-001",
                      "nifEstacion": "B12345678"
                    },
                    {
                      "fotoIndice": 1,
                      "proveedor": "REPSOL",
                      "estacion": "E.S. EJEMPLO",
                      "direccion": "Calle Falsa 123",
                      "fecha": "2026-09-15",
                      "hora": "10:31",
                      "numTarjeta4ultimos": "1234",
                      "matricula": "1234-ABC",
                      "kms": null,
                      "producto": "ADBLUE",
                      "litros": 10.00,
                      "precioLitro": 0.95,
                      "importeTotal": 9.50,
                      "numRecibo": "REC-001",
                      "nifEstacion": "B12345678"
                    }
                  ]
                }
                """;

        JsonNode raiz = mapper.readTree(json);
        List<LineaTicketExtraida> lineas = parser.construirResultado(raiz);

        assertThat(lineas).hasSize(2);

        LineaTicketExtraida l0 = lineas.get(0);
        assertThat(l0.fotoIndice()).isZero();
        assertThat(l0.estacion()).isEqualTo("E.S. EJEMPLO");
        assertThat(l0.fechaHora()).isEqualTo(LocalDateTime.of(2026, 9, 15, 10, 30));
        assertThat(l0.producto()).isEqualTo("DIESEL");
        assertThat(l0.litros()).isEqualByComparingTo(new BigDecimal("45.20"));
        assertThat(l0.importeTotal()).isEqualByComparingTo(new BigDecimal("74.51"));
        assertThat(l0.kms()).isEqualTo(50000);

        LineaTicketExtraida l1 = lineas.get(1);
        assertThat(l1.fotoIndice()).isEqualTo(1);
        assertThat(l1.producto()).isEqualTo("ADBLUE");
        assertThat(l1.kms()).isNull();
        assertThat(l1.estacion()).isEqualTo(l0.estacion())
                .as("el mismo ticket repite estacion/matricula en varias fotos");
    }

    @Test
    @DisplayName("campos null en el JSON se mapean a null, sin lanzar excepcion")
    void construirResultado_camposNull_seMapeanComoNull() throws Exception {
        String json = """
                {
                  "lineas": [
                    {
                      "fotoIndice": 0,
                      "proveedor": null,
                      "estacion": "E.S. EJEMPLO",
                      "direccion": null,
                      "fecha": "2026-09-15",
                      "hora": null,
                      "numTarjeta4ultimos": null,
                      "matricula": null,
                      "kms": null,
                      "producto": "DIESEL",
                      "litros": 10.0,
                      "precioLitro": 1.5,
                      "importeTotal": 15.0,
                      "numRecibo": null,
                      "nifEstacion": null
                    }
                  ]
                }
                """;

        JsonNode raiz = mapper.readTree(json);
        List<LineaTicketExtraida> lineas = parser.construirResultado(raiz);

        assertThat(lineas).hasSize(1);
        LineaTicketExtraida l0 = lineas.get(0);
        assertThat(l0.proveedor()).isNull();
        assertThat(l0.direccion()).isNull();
        assertThat(l0.numTarjeta4ultimos()).isNull();
        assertThat(l0.matricula()).isNull();
        assertThat(l0.kms()).isNull();
        assertThat(l0.numRecibo()).isNull();
        assertThat(l0.nifEstacion()).isNull();
        // hora ausente -> fechaHora cae a medianoche de la fecha, no null
        // (siempre hay fecha; es la hora la que puede faltar)
        assertThat(l0.fechaHora()).isEqualTo(LocalDateTime.of(2026, 9, 15, 0, 0));
    }

    @Test
    @DisplayName("lista de lineas vacia no lanza excepcion")
    void construirResultado_listaVacia() throws Exception {
        JsonNode raiz = mapper.readTree("{\"lineas\": []}");
        List<LineaTicketExtraida> lineas = parser.construirResultado(raiz);
        assertThat(lineas).isEmpty();
    }
}
