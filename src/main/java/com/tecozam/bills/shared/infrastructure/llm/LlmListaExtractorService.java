package com.tecozam.bills.shared.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Extrae una LISTA de filas de un PDF usando un LLM (compartido por el
 * import PDF+IA de maestros: Trabajador, Vehiculo, Tarjeta — ver
 * odd/tasks/maestros-import-pdf-ia.md T1). Mismo patron de llamada a OpenAI
 * que {@link com.tecozam.bills.factura.infrastructure.parser.LlmFacturaParser}
 * (misma property de configuracion {@code app.openai.*}, mismo modo
 * {@code json_schema} estricto), pero genérico: cada dominio aporta su
 * propio prompt y el schema de UNA fila, y este servicio los envuelve en un
 * array {@code filas} de nivel superior (OpenAI structured output exige un
 * objeto raiz, no un array suelto).
 *
 * <p>Este servicio NO valida reglas de negocio ni decide si una fila es
 * valida — devuelve las filas tal cual las interpreto el modelo, en bruto.
 * La validacion la hace el {@code *XlsxRowParser.validar(...)} de cada
 * dominio (el mismo que ya usa el import Excel), para garantizar que PDF y
 * Excel nunca apliquen reglas distintas.
 */
@Slf4j
@Component
public class LlmListaExtractorService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${app.openai.api-key:}")
    private String apiKey;

    @Value("${app.openai.model:gpt-4.1-mini}")
    private String model;

    public List<JsonNode> extraerFilas(byte[] pdfBytes, String systemPrompt, ObjectNode filaSchema) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "El import PDF+IA está activado pero falta la clave OPENAI_API_KEY.");
        }

        String texto = extraerTextoPdf(pdfBytes);
        String respuestaJson = callOpenAI(texto, systemPrompt, filaSchema);
        JsonNode raiz = parsearJson(respuestaJson);

        List<JsonNode> filas = new ArrayList<>();
        for (JsonNode fila : raiz.path("filas")) {
            filas.add(fila);
        }
        log.info("LlmListaExtractorService: {} filas extraidas de PDF ({} bytes)", filas.size(), pdfBytes.length);
        return filas;
    }

    private String extraerTextoPdf(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    private String callOpenAI(String texto, String systemPrompt, ObjectNode filaSchema) throws Exception {
        ObjectNode requestBody = MAPPER.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.0);

        ArrayNode messages = requestBody.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", "=== TEXTO DEL DOCUMENTO ===\n" + texto);

        requestBody.set("response_format", buildResponseFormat(filaSchema));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(180))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("[LlmListaExtractorService] OpenAI devolvió {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Error del servicio de IA al extraer el documento (HTTP " + response.statusCode() + ")");
        }

        JsonNode body = MAPPER.readTree(response.body());
        JsonNode contenido = body.at("/choices/0/message/content");
        if (contenido.isMissingNode() || contenido.isNull()) {
            throw new RuntimeException("Respuesta inesperada del servicio de IA: sin contenido");
        }
        return contenido.asText();
    }

    /**
     * Envuelve el schema de UNA fila (aportado por cada dominio) en el
     * objeto raiz {@code {"filas": [...]}} que exige el modo json_schema
     * estricto de OpenAI.
     */
    private ObjectNode buildResponseFormat(ObjectNode filaSchema) {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode props = schema.putObject("properties");
        ObjectNode filasArray = props.putObject("filas");
        filasArray.put("type", "array");
        filasArray.set("items", filaSchema);

        schema.putArray("required").add("filas");

        ObjectNode jsonSchemaWrapper = MAPPER.createObjectNode();
        jsonSchemaWrapper.put("name", "lista_extraida");
        jsonSchemaWrapper.put("strict", true);
        jsonSchemaWrapper.set("schema", schema);

        ObjectNode responseFormat = MAPPER.createObjectNode();
        responseFormat.put("type", "json_schema");
        responseFormat.set("json_schema", jsonSchemaWrapper);
        return responseFormat;
    }

    private JsonNode parsearJson(String json) throws Exception {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("El servicio de IA devolvió una respuesta que no es JSON válido: " + e.getMessage(), e);
        }
    }

    /** Helper para que cada dominio construya un campo string opcional (nullable) del schema de su fila. */
    public static void nullableString(ObjectNode props, String name) {
        ObjectNode node = props.putObject(name);
        node.putArray("type").add("string").add("null");
    }
}
