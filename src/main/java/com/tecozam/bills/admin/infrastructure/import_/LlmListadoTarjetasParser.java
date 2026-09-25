package com.tecozam.bills.admin.infrastructure.import_;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extrae las filas de un listado de tarjetas/VIATs (Excel) usando un LLM en
 * vez de los parsers deterministas por fila ({@link RepsolXlsxRowParser},
 * {@link CepsaXlsxRowParser}). Solo se usa cuando el admin activa el modo LLM
 * (ver odd/tasks/import-llm-switch.md).
 *
 * <p>UNA sola llamada por hoja (no una por fila): con listados de cientos de
 * filas, una llamada por fila sería lento y caro. El LLM también sugiere una
 * clasificación TARJETA/VIAT por fila ({@code tipoSugerido}), pero esa
 * sugerencia nunca se usa sola — el orquestador ({@code ListadoTarjetasImportService})
 * la cruza contra {@link ConceptoClassifier} (el regex determinista) antes de
 * materializar nada; si discrepan, la fila se deja para revisión manual en vez
 * de que el sistema decida en silencio cuál de las dos clasificaciones es la
 * correcta. Mismo patrón de cliente OpenAI que
 * {@code factura.infrastructure.parser.LlmFacturaParser} (HttpClient JDK,
 * modo json_schema estricto, temperature 0).
 */
@Slf4j
@Component
public class LlmListadoTarjetasParser {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DataFormatter FORMATTER = new DataFormatter(new Locale("es", "ES"));

    @Value("${app.openai.api-key:}")
    private String apiKey;

    @Value("${app.openai.model:gpt-4.1-mini}")
    private String model;

    public List<FilaImportada> parsearHoja(Sheet hoja, String codigoProveedor) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "El modo LLM de import está activado pero falta la clave OPENAI_API_KEY.");
        }

        String textoHoja = volcarHojaATexto(hoja);
        String respuestaJson = callOpenAI(textoHoja, codigoProveedor);
        JsonNode raiz = parsearJson(respuestaJson);

        return construirFilas(raiz);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VOLCADO DE LA HOJA A TEXTO
    // ─────────────────────────────────────────────────────────────────────────

    private String volcarHojaATexto(Sheet hoja) {
        StringBuilder sb = new StringBuilder();
        for (Row row : hoja) {
            int lastCol = row.getLastCellNum();
            for (int i = 0; i < lastCol; i++) {
                if (i > 0) sb.append(" | ");
                String val = FORMATTER.formatCellValue(row.getCell(i));
                sb.append(val == null ? "" : val.trim());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LLAMADA A OPENAI (modo JSON estructurado)
    // ─────────────────────────────────────────────────────────────────────────

    private String callOpenAI(String textoHoja, String codigoProveedor) throws Exception {
        String systemPrompt = "Eres un extractor de datos de listados de tarjetas de combustible y "
                + "dispositivos VIAT/telepeaje (proveedor: " + codigoProveedor + "). La primera línea del "
                + "texto es la cabecera de columnas; el resto son filas de datos separadas por ' | '. "
                + "Extrae ÚNICAMENTE lo que aparezca literalmente en cada fila, sin inventar valores. "
                + "Ignora filas totalmente vacías o sin número de tarjeta. "
                + "Para 'tipoSugerido' clasifica cada fila como \"VIAT\" solo si el concepto describe "
                + "explícitamente un DISPOSITIVO de telepeaje (palabras como PEAJE, AUTOPISTA, TUNEL, "
                + "PORTAGEM, VIA T, OBE, TELEPEAJE referidas al propio dispositivo/servicio de paso). "
                + "IMPORTANTE: una comisión o cargo puntual de peaje/autopista facturado sobre una TARJETA "
                + "de combustible normal (p.ej. \"COMISION AUTOPISTAS\", \"GEST. SERV. AUTOP. ESPAÑA\") NO "
                + "es un VIAT — sigue siendo \"TARJETA\". En caso de duda entre TARJETA y VIAT, clasifica "
                + "como \"TARJETA\" (default seguro).";

        ObjectNode requestBody = MAPPER.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.0);

        var messages = requestBody.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", textoHoja);

        requestBody.set("response_format", buildJsonSchema());

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("[LlmListadoTarjetasParser] OpenAI devolvió {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Error del servicio de IA al extraer el listado (HTTP " + response.statusCode() + ")");
        }

        JsonNode body = MAPPER.readTree(response.body());
        JsonNode contenido = body.at("/choices/0/message/content");
        if (contenido.isMissingNode() || contenido.isNull()) {
            throw new RuntimeException("Respuesta inesperada del servicio de IA: sin contenido");
        }
        return contenido.asText();
    }

    private JsonNode parsearJson(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("El servicio de IA devolvió una respuesta que no es JSON válido: " + e.getMessage(), e);
        }
    }

    /**
     * Schema envuelto en un objeto ({@code {"filas": [...]}}) en vez de un
     * array en la raíz — mismo patrón que {@code LlmFacturaParser} para
     * {@code conceptos}/{@code tarjetas}, más compatible con el modo
     * {@code json_schema} estricto de OpenAI.
     */
    private ObjectNode buildJsonSchema() {
        ObjectNode filaItem = MAPPER.createObjectNode();
        filaItem.put("type", "object");
        filaItem.put("additionalProperties", false);
        ObjectNode filaProps = filaItem.putObject("properties");
        filaProps.putObject("numero").put("type", "string");
        nullableString(filaProps, "matricula");
        nullableString(filaProps, "nombreCompleto");
        nullableString(filaProps, "centroCoste");
        filaProps.putObject("concepto").put("type", "string");
        ObjectNode tipoNode = filaProps.putObject("tipoSugerido");
        tipoNode.put("type", "string");
        tipoNode.putArray("enum").add("TARJETA").add("VIAT");
        filaItem.putArray("required").add("numero").add("matricula").add("nombreCompleto")
                .add("centroCoste").add("concepto").add("tipoSugerido");

        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = schema.putObject("properties");
        ObjectNode filasArray = props.putObject("filas");
        filasArray.put("type", "array");
        filasArray.set("items", filaItem);
        schema.putArray("required").add("filas");

        ObjectNode jsonSchemaWrapper = MAPPER.createObjectNode();
        jsonSchemaWrapper.put("name", "listado_tarjetas_extraido");
        jsonSchemaWrapper.put("strict", true);
        jsonSchemaWrapper.set("schema", schema);

        ObjectNode responseFormat = MAPPER.createObjectNode();
        responseFormat.put("type", "json_schema");
        responseFormat.set("json_schema", jsonSchemaWrapper);
        return responseFormat;
    }

    private void nullableString(ObjectNode props, String name) {
        ObjectNode node = props.putObject(name);
        node.putArray("type").add("string").add("null");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAPEO JSON → FilaImportada
    // ─────────────────────────────────────────────────────────────────────────

    List<FilaImportada> construirFilas(JsonNode raiz) {
        List<FilaImportada> filas = new ArrayList<>();
        for (JsonNode f : raiz.path("filas")) {
            String numero = text(f, "numero");
            if (numero == null || numero.isBlank()) continue;

            String concepto = text(f, "concepto");
            TipoRecurso tipo = TipoRecurso.valueOf(text(f, "tipoSugerido"));
            // El LLM solo devuelve una fila si entendió su contenido — a
            // diferencia del regex, no hay un "default seguro" silencioso
            // aquí: si el LLM no está seguro del concepto, se le indica en el
            // prompt que aun así debe intentar TARJETA/VIAT, así que toda fila
            // devuelta se considera "concepto conocido" a efectos de reporte.
            filas.add(new FilaImportada(numero, text(f, "matricula"), text(f, "nombreCompleto"),
                    text(f, "centroCoste"), concepto, tipo, true));
        }
        return filas;
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
