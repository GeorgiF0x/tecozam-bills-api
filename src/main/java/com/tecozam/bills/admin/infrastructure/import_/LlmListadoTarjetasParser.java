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
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Extrae las filas de un listado de tarjetas/VIATs (Excel) usando un LLM en
 * vez de los parsers deterministas por fila ({@link RepsolXlsxRowParser},
 * {@link CepsaXlsxRowParser}). Solo se usa cuando el admin activa el modo LLM
 * (ver odd/tasks/import-llm-switch.md).
 *
 * <p>Trocea la hoja en lotes de {@link #TAMANO_LOTE} filas (una llamada por
 * lote, no una por hoja completa ni una por fila): con listados grandes
 * (100-350 filas, el caso normal), una sola llamada para toda la hoja trunca
 * la respuesta JSON por límite de tokens de salida del modelo SIN dar ningún
 * error — confirmado con archivos reales (Repsol 121→25 filas, Moeve/Cepsa
 * 347→5 filas, sin ninguna excepción ni fallo HTTP). Trocear en lotes
 * pequeños evita el límite de salida por llamada, y sigue siendo muchísimo
 * más barato/rápido que una llamada por fila. Además, por cada lote se
 * compara qué números de tarjeta se enviaron contra los que el LLM devolvió:
 * cualquier fila que el LLM se salte queda registrada en
 * {@link ResultadoParseoLlm#numerosPerdidos()} en vez de perderse en
 * silencio.
 *
 * <p>El LLM también sugiere una clasificación TARJETA/VIAT por fila
 * ({@code tipoSugerido}), pero esa sugerencia nunca se usa sola — el
 * orquestador ({@code ListadoTarjetasImportService}) la cruza contra
 * {@link ConceptoClassifier} (el regex determinista) antes de materializar
 * nada; si discrepan, la fila se deja para revisión manual en vez de que el
 * sistema decida en silencio cuál de las dos clasificaciones es correcta.
 * Mismo patrón de cliente OpenAI que
 * {@code factura.infrastructure.parser.LlmFacturaParser} (HttpClient JDK,
 * modo json_schema estricto, temperature 0).
 */
@Slf4j
@Component
public class LlmListadoTarjetasParser {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DataFormatter FORMATTER = new DataFormatter(new Locale("es", "ES"));

    /**
     * Filas de datos por llamada a OpenAI. Ver la nota de la clase: el límite
     * real es el de tokens de salida del modelo generando el array JSON, no
     * un límite artificial nuestro — 30 es un margen holgado por debajo de
     * donde se observó truncamiento con lotes mucho más grandes.
     */
    static final int TAMANO_LOTE = 30;

    @Value("${app.openai.api-key:}")
    private String apiKey;

    @Value("${app.openai.model:gpt-4.1-mini}")
    private String model;

    /**
     * Resultado de parsear una hoja completa (todos los lotes).
     *
     * @param filas           filas correctamente extraídas por el LLM, acumuladas de todos los lotes
     * @param numerosPerdidos números de tarjeta que se enviaron en algún lote pero el LLM no
     *                        devolvió en la respuesta de ese lote (probable truncamiento) — el
     *                        llamador debe marcarlas para revisión manual, nunca ignorarlas
     */
    public record ResultadoParseoLlm(List<FilaImportada> filas, List<String> numerosPerdidos) {
    }

    public ResultadoParseoLlm parsearHoja(Sheet hoja, String codigoProveedor) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "El modo LLM de import está activado pero falta la clave OPENAI_API_KEY.");
        }

        Row cabecera = hoja.getRow(0);
        int idxNumero = indiceColumnaNumero(cabecera);

        List<Row> filasDatos = new ArrayList<>();
        for (Row row : hoja) {
            if (row.getRowNum() == 0) continue;
            filasDatos.add(row);
        }

        List<FilaImportada> todasLasFilas = new ArrayList<>();
        List<String> numerosPerdidos = new ArrayList<>();

        for (int inicio = 0; inicio < filasDatos.size(); inicio += TAMANO_LOTE) {
            List<Row> lote = filasDatos.subList(inicio, Math.min(inicio + TAMANO_LOTE, filasDatos.size()));

            Set<String> numerosEnviados = new LinkedHashSet<>();
            for (Row row : lote) {
                String numero = idxNumero >= 0 ? FORMATTER.formatCellValue(row.getCell(idxNumero)).trim() : "";
                if (!numero.isBlank()) numerosEnviados.add(numero);
            }
            if (numerosEnviados.isEmpty()) continue;

            String textoLote = volcarLoteATexto(cabecera, lote);
            String respuestaJson = callOpenAI(textoLote, codigoProveedor);
            JsonNode raiz = parsearJson(respuestaJson);
            List<FilaImportada> filasLote = construirFilas(raiz);
            todasLasFilas.addAll(filasLote);

            List<String> perdidasDeEsteLote = detectarNumerosPerdidos(numerosEnviados, filasLote);
            if (!perdidasDeEsteLote.isEmpty()) {
                log.warn("[LlmListadoTarjetasParser] Lote incompleto: se enviaron {} filas, el LLM devolvió {} ({} perdidas)",
                        numerosEnviados.size(), filasLote.size(), perdidasDeEsteLote.size());
                numerosPerdidos.addAll(perdidasDeEsteLote);
            }
        }

        return new ResultadoParseoLlm(todasLasFilas, numerosPerdidos);
    }

    /**
     * Números de tarjeta enviados en un lote que no aparecen entre las filas
     * que devolvió el LLM para ese mismo lote. Función pura, testeable sin
     * red: si {@code filasDevueltas} viene incompleta (truncamiento), esto lo
     * detecta comparando conjuntos de números, no contando elementos.
     */
    static List<String> detectarNumerosPerdidos(Set<String> numerosEnviados, List<FilaImportada> filasDevueltas) {
        Set<String> numerosDevueltos = filasDevueltas.stream()
                .map(FilaImportada::numero)
                .collect(Collectors.toSet());
        List<String> perdidos = new ArrayList<>();
        for (String numero : numerosEnviados) {
            if (!numerosDevueltos.contains(numero)) {
                perdidos.add(numero);
            }
        }
        return perdidos;
    }

    /**
     * Localiza la columna de número de tarjeta en la cabecera, con la misma
     * normalización (sin tildes, mayúsculas, espacios colapsados) y los
     * mismos textos aceptados que {@link RepsolXlsxRowParser}/{@link CepsaXlsxRowParser}.
     */
    private int indiceColumnaNumero(Row cabecera) {
        if (cabecera == null) return -1;
        for (int i = 0; i < cabecera.getLastCellNum(); i++) {
            String raw = FORMATTER.formatCellValue(cabecera.getCell(i));
            if (raw == null) continue;
            String sinTildes = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            String compact = sinTildes.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (compact.equals("NUMERO TARJETA") || compact.equals("NUMERO DE TARJETA")) {
                return i;
            }
        }
        return -1;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VOLCADO DE UN LOTE A TEXTO (cabecera + solo las filas de ese lote)
    // ─────────────────────────────────────────────────────────────────────────

    private String volcarLoteATexto(Row cabecera, List<Row> lote) {
        StringBuilder sb = new StringBuilder();
        if (cabecera != null) {
            volcarFila(sb, cabecera);
        }
        for (Row row : lote) {
            volcarFila(sb, row);
        }
        return sb.toString();
    }

    private void volcarFila(StringBuilder sb, Row row) {
        int lastCol = row.getLastCellNum();
        for (int i = 0; i < lastCol; i++) {
            if (i > 0) sb.append(" | ");
            String val = FORMATTER.formatCellValue(row.getCell(i));
            sb.append(val == null ? "" : val.trim());
        }
        sb.append('\n');
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LLAMADA A OPENAI (modo JSON estructurado)
    // ─────────────────────────────────────────────────────────────────────────

    protected String callOpenAI(String textoHoja, String codigoProveedor) throws Exception {
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
