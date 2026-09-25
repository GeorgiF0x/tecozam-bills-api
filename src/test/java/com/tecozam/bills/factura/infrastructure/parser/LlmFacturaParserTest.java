package com.tecozam.bills.factura.infrastructure.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecozam.bills.factura.domain.Factura;
import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.domain.TarjetaResumen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No llama a la API real de OpenAI (sin red, sin gastar tokens): prueba
 * únicamente el mapeo JSON → entidades ({@link LlmFacturaParser#construirResultado})
 * con una respuesta de ejemplo fija, que es la parte con lógica propia — la
 * llamada HTTP en sí reutiliza el mismo patrón ya usado (y probado en
 * producción) por {@code AsistenteService}.
 */
class LlmFacturaParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmFacturaParser parser = new LlmFacturaParser("REPSOL", "fake-key", "gpt-4.1-mini");

    @Test
    @DisplayName("mapea una respuesta JSON completa a Factura/TarjetaResumen/Operacion/FacturaConceptoResumen")
    void construirResultado_mapeaTodosLosCampos() throws Exception {
        String json = """
                {
                  "numFactura": "FA-2026-001",
                  "fecha": "2026-07-31",
                  "periodoDesde": "2026-07-01",
                  "periodoHasta": "2026-07-31",
                  "vencimiento": "2026-08-15",
                  "numCuenta": "123456",
                  "nifCliente": "ESB12345678",
                  "iban": "ES9121000418450200051332",
                  "baseImponible": 100.00,
                  "totalIva": 21.00,
                  "totalFactura": 121.00,
                  "conceptos": [
                    {
                      "conceptoOriginal": "DIESEL E+",
                      "conceptoUnificado": "DIESEL",
                      "cantidad": 50.5,
                      "baseImponible": 100.00,
                      "tipoIva": 21.00,
                      "cuotaIva": 21.00,
                      "importe": 121.00
                    }
                  ],
                  "tarjetas": [
                    {
                      "numTarjeta": "1234567890123456",
                      "alias": "1234-ABC",
                      "conductor": "Juan Perez",
                      "operaciones": [
                        {
                          "referencia": "REF001",
                          "fechaHora": "2026-07-15T10:30:00",
                          "conceptoOriginal": "DIESEL E+ NEOTECH",
                          "conceptoUnificado": "DIESEL",
                          "establecimiento": "E.S. EJEMPLO",
                          "cantidad": 50.5,
                          "precioUnitario": 1.648,
                          "importeTotal": 121.00
                        }
                      ]
                    }
                  ]
                }
                """;

        JsonNode raiz = mapper.readTree(json);
        FacturaParseResult result = parser.construirResultado(raiz);

        Factura factura = result.getFactura();
        assertThat(factura.getNumFactura()).isEqualTo("FA-2026-001");
        assertThat(factura.getFecha()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(factura.getPeriodoDesde()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(factura.getPeriodoHasta()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(factura.getTotalFactura()).isEqualByComparingTo(new BigDecimal("121.00"));

        assertThat(result.getConceptos()).hasSize(1);
        assertThat(result.getConceptos().get(0).getConceptoUnificado()).isEqualTo("DIESEL");
        assertThat(result.getConceptos().get(0).getFactura()).isSameAs(factura);

        assertThat(result.getTarjetaResumenes()).hasSize(1);
        TarjetaResumen tr = result.getTarjetaResumenes().get(0);
        assertThat(tr.getNumTarjeta()).isEqualTo("1234567890123456");
        assertThat(tr.getFactura()).isSameAs(factura);

        assertThat(tr.getOperaciones()).hasSize(1);
        Operacion op = tr.getOperaciones().get(0);
        assertThat(op.getFechaHora()).isEqualTo(LocalDateTime.of(2026, 7, 15, 10, 30));
        assertThat(op.getConceptoUnificado()).isEqualTo("DIESEL");
        assertThat(op.getImporteTotal()).isEqualByComparingTo(new BigDecimal("121.00"));
        assertThat(op.getTarjetaResumen()).isSameAs(tr);
        assertThat(op.getFactura()).isSameAs(factura);
    }

    @Test
    @DisplayName("campos null en el JSON se mapean a null, sin lanzar excepcion")
    void construirResultado_camposNull_seMapeanComoNull() throws Exception {
        String json = """
                {
                  "numFactura": "FA-2026-002",
                  "fecha": "2026-07-31",
                  "periodoDesde": "2026-07-01",
                  "periodoHasta": "2026-07-31",
                  "vencimiento": null,
                  "numCuenta": null,
                  "nifCliente": null,
                  "iban": null,
                  "baseImponible": 0,
                  "totalIva": 0,
                  "totalFactura": 0,
                  "conceptos": [],
                  "tarjetas": []
                }
                """;

        JsonNode raiz = mapper.readTree(json);
        FacturaParseResult result = parser.construirResultado(raiz);

        assertThat(result.getFactura().getVencimiento()).isNull();
        assertThat(result.getFactura().getNumCuenta()).isNull();
        assertThat(result.getConceptos()).isEmpty();
        assertThat(result.getTarjetaResumenes()).isEmpty();
    }
}
