package com.tecozam.bills.admin.infrastructure.import_;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    // ─────────────────────────────────────────────────────────────────────────
    // detectarNumerosPerdidos — funcion pura, sin red
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("detectarNumerosPerdidos: ninguno perdido si todos los enviados vuelven")
    void detectarNumerosPerdidos_todosVuelven_listaVacia() {
        Set<String> enviados = new LinkedHashSet<>(List.of("111", "222", "333"));
        List<FilaImportada> devueltas = List.of(
                filaDe("111"), filaDe("222"), filaDe("333"));

        List<String> perdidos = LlmListadoTarjetasParser.detectarNumerosPerdidos(enviados, devueltas);

        assertThat(perdidos).isEmpty();
    }

    @Test
    @DisplayName("detectarNumerosPerdidos: detecta los numeros enviados que no volvieron (truncamiento)")
    void detectarNumerosPerdidos_respuestaTruncada_detectaLosQueFaltan() {
        Set<String> enviados = new LinkedHashSet<>(List.of("111", "222", "333", "444"));
        List<FilaImportada> devueltas = List.of(filaDe("111"), filaDe("222")); // 333 y 444 se perdieron

        List<String> perdidos = LlmListadoTarjetasParser.detectarNumerosPerdidos(enviados, devueltas);

        assertThat(perdidos).containsExactlyInAnyOrder("333", "444");
    }

    private static FilaImportada filaDe(String numero) {
        return new FilaImportada(numero, null, null, null, "DIESEL", TipoRecurso.TARJETA, true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // parsearHoja: troceo en lotes (sin red real — subclase que sobreescribe callOpenAI)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Subclase de test: {@code callOpenAI} no es un componente inyectable
     * (la clase no toma un HttpClient por constructor), así que para probar
     * el troceo en lotes sin red real, se sobreescribe el punto exacto donde
     * se haría la llamada HTTP. {@code apiKey} se rellena por reflexion
     * porque en produccion la inyecta Spring via {@code @Value}.
     */
    private static class ParserConRespuestasFijas extends LlmListadoTarjetasParser {
        final Deque<String> respuestas;
        final List<String> textosRecibidos = new ArrayList<>();

        ParserConRespuestasFijas(Deque<String> respuestas) throws Exception {
            this.respuestas = respuestas;
            setPrivateField("apiKey", "sk-test-no-real");
            setPrivateField("model", "gpt-4.1-mini");
        }

        private void setPrivateField(String name, String value) throws Exception {
            Field f = LlmListadoTarjetasParser.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(this, value);
        }

        @Override
        protected String callOpenAI(String textoLote, String codigoProveedor) {
            textosRecibidos.add(textoLote);
            return respuestas.poll();
        }
    }

    private static Sheet hojaConFilas(int cantidadFilas) {
        Workbook wb = new XSSFWorkbook();
        Sheet hoja = wb.createSheet("Hoja1");
        Row cabecera = hoja.createRow(0);
        cabecera.createCell(0).setCellValue("NUMERO TARJETA");
        cabecera.createCell(1).setCellValue("DES_PRODU");
        for (int i = 1; i <= cantidadFilas; i++) {
            Row row = hoja.createRow(i);
            row.createCell(0).setCellValue("NUM-" + i);
            row.createCell(1).setCellValue("DIESEL");
        }
        return hoja;
    }

    private static String respuestaConNumeros(String... numeros) {
        StringBuilder sb = new StringBuilder("{\"filas\": [");
        for (int i = 0; i < numeros.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"numero\": \"").append(numeros[i]).append("\", \"matricula\": null, "
                    + "\"nombreCompleto\": null, \"centroCoste\": null, \"concepto\": \"DIESEL\", "
                    + "\"tipoSugerido\": \"TARJETA\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    @Test
    @DisplayName("parsearHoja: una hoja pequena (menos de un lote) hace una sola llamada")
    void parsearHoja_hojaPequena_unaSolaLlamada() throws Exception {
        Sheet hoja = hojaConFilas(5);
        Deque<String> respuestas = new ArrayDeque<>(List.of(
                respuestaConNumeros("NUM-1", "NUM-2", "NUM-3", "NUM-4", "NUM-5")));
        ParserConRespuestasFijas parser = new ParserConRespuestasFijas(respuestas);

        LlmListadoTarjetasParser.ResultadoParseoLlm resultado = parser.parsearHoja(hoja, "REPSOL");

        assertThat(parser.textosRecibidos).hasSize(1);
        assertThat(resultado.filas()).hasSize(5);
        assertThat(resultado.numerosPerdidos()).isEmpty();
    }

    @Test
    @DisplayName("parsearHoja: una hoja de mas de TAMANO_LOTE filas se divide en varias llamadas")
    void parsearHoja_hojaGrande_seDivideEnVariosLotes() throws Exception {
        int totalFilas = LlmListadoTarjetasParser.TAMANO_LOTE + 10; // fuerza 2 lotes
        Sheet hoja = hojaConFilas(totalFilas);

        String[] numerosLote1 = new String[LlmListadoTarjetasParser.TAMANO_LOTE];
        for (int i = 0; i < numerosLote1.length; i++) numerosLote1[i] = "NUM-" + (i + 1);
        String[] numerosLote2 = new String[10];
        for (int i = 0; i < numerosLote2.length; i++) numerosLote2[i] = "NUM-" + (LlmListadoTarjetasParser.TAMANO_LOTE + i + 1);

        Deque<String> respuestas = new ArrayDeque<>(List.of(
                respuestaConNumeros(numerosLote1),
                respuestaConNumeros(numerosLote2)));
        ParserConRespuestasFijas parser = new ParserConRespuestasFijas(respuestas);

        LlmListadoTarjetasParser.ResultadoParseoLlm resultado = parser.parsearHoja(hoja, "REPSOL");

        assertThat(parser.textosRecibidos).hasSize(2);
        assertThat(resultado.filas()).hasSize(totalFilas);
        assertThat(resultado.numerosPerdidos()).isEmpty();
    }

    @Test
    @DisplayName("parsearHoja: si un lote vuelve truncado, las filas perdidas de ese lote se reportan")
    void parsearHoja_loteTruncado_reportaNumerosPerdidos() throws Exception {
        Sheet hoja = hojaConFilas(5);
        // El LLM solo devuelve 3 de las 5 filas del unico lote -> 2 perdidas
        Deque<String> respuestas = new ArrayDeque<>(List.of(
                respuestaConNumeros("NUM-1", "NUM-2", "NUM-3")));
        ParserConRespuestasFijas parser = new ParserConRespuestasFijas(respuestas);

        LlmListadoTarjetasParser.ResultadoParseoLlm resultado = parser.parsearHoja(hoja, "REPSOL");

        assertThat(resultado.filas()).hasSize(3);
        assertThat(resultado.numerosPerdidos()).containsExactlyInAnyOrder("NUM-4", "NUM-5");
    }
}
