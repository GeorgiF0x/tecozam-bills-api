package com.tecozam.bills.ticket.infrastructure.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Extrae las líneas de un ticket de combustible/peaje a partir de N fotos
 * (el cliente parte tickets largos en varias fotos) usando un LLM con salida
 * estructurada ({@code json_schema}, {@code strict:true}) — a diferencia del
 * OCR actual de tickets ({@code TicketController.performOcr}), que usa un
 * prompt de texto libre sin schema, causa más probable de que el formulario
 * se rellene mal en tickets largos (ver odd/tasks/ticket-multi-foto.md).
 *
 * Las N fotos se mandan en UNA sola llamada (todas en el mismo mensaje de
 * Vision), no N llamadas sueltas fusionadas a mano: da mejor contexto
 * cruzado entre fotos y evita alucinar valores que una foto aislada no deja
 * ver con claridad (requisito explícito del usuario).
 *
 * Esta clase es deliberadamente independiente de {@code TicketController}/
 * {@code TicketService} — el wiring a los endpoints reales (sustituir
 * {@code performOcr}, aceptar {@code MultipartFile[]}, persistir fotos) es
 * otra tarea (T3). Aquí solo se construye y se prueba el parser en
 * aislamiento, para no dejar el proyecto a medio romper.
 *
 * A diferencia de {@code LlmFacturaParser} (que sí necesita instanciarse por
 * importación, porque el prompt depende del proveedor), este parser no tiene
 * ningún parámetro por-llamada más allá de las fotos — por eso es un
 * {@code @Component} singleton normal, sin factory.
 */
@Slf4j
@Component
public class LlmTicketParser {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiKey;
    private final String model;

    public LlmTicketParser(
            @Value("${app.openai.api-key:}") String apiKey,
            @Value("${app.openai.model:gpt-4.1-mini}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public List<LineaTicketExtraida> parse(List<ImagenTicket> fotos) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Falta OPENAI_API_KEY para el OCR de tickets por IA.");
        }
        if (fotos == null || fotos.isEmpty()) {
            throw new IllegalArgumentException("Se necesita al menos una foto del ticket.");
        }

        String respuestaJson = callOpenAI(fotos);
        JsonNode raiz = parsearJson(respuestaJson);
        return construirResultado(raiz);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LLAMADA A OPENAI (modo JSON estructurado, N imágenes en un mismo mensaje)
    // ─────────────────────────────────────────────────────────────────────────

    private String callOpenAI(List<ImagenTicket> fotos) throws Exception {
        String systemPrompt = "Eres un extractor de datos de tickets de gasolinera o peaje de una flota de "
                + "vehículos. El ticket puede estar en español o portugués; responde con los valores en el "
                + "idioma original del ticket, salvo que se indique lo contrario. "
                + "Te llegan " + fotos.size() + " fotos que son PARTES DEL MISMO TICKET (el ticket era "
                + "demasiado largo para una sola foto). Campos como estación, dirección, matrícula, NIF de la "
                + "estación y los últimos 4 dígitos de la tarjeta suelen repetirse en varias fotos porque son "
                + "el mismo ticket — usa esa repetición para confirmarlos, no los inventes si ninguna foto los "
                + "muestra con claridad. "
                + "Devuelve una línea por cada producto/operación que veas en cualquiera de las fotos (puede "
                + "haber más de una línea por foto si el ticket detalla varios productos). "
                + "Para cada línea indica en 'fotoIndice' el número de foto (0 = la primera) de la que la "
                + "sacaste. "
                + "Extrae ÚNICAMENTE los datos que aparezcan literalmente en alguna foto. No inventes ni "
                + "calcules valores que no estén presentes — si un campo no es legible en ninguna foto, "
                + "devuelve null para ese campo en esa línea. Es preferible devolver null a adivinar. "
                + "Fechas en formato yyyy-MM-dd, horas en HH:mm. Usa punto como separador decimal.";

        ArrayNode content = MAPPER.createArrayNode();
        content.addObject().put("type", "text").put("text", systemPrompt);
        for (ImagenTicket foto : fotos) {
            String base64 = Base64.getEncoder().encodeToString(foto.bytes());
            String mimeType = foto.mimeType() != null ? foto.mimeType() : "image/jpeg";
            ObjectNode imageBlock = content.addObject();
            imageBlock.put("type", "image_url");
            imageBlock.putObject("image_url").put("url", "data:" + mimeType + ";base64," + base64);
        }

        ObjectNode requestBody = MAPPER.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.0);
        ArrayNode messages = requestBody.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.set("content", content);
        requestBody.set("response_format", buildJsonSchema());

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(requestBody)))
                .timeout(Duration.ofSeconds(90))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("[LlmTicketParser] OpenAI devolvió {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Error del servicio de IA al extraer el ticket (HTTP " + response.statusCode() + ")");
        }

        JsonNode body = MAPPER.readTree(response.body());
        JsonNode contenido = body.at("/choices/0/message/content");
        if (contenido.isMissingNode() || contenido.isNull()) {
            throw new RuntimeException("Respuesta inesperada del servicio de IA: sin contenido");
        }
        return contenido.asText();
    }

    private JsonNode parsearJson(String json) throws Exception {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("El servicio de IA devolvió una respuesta que no es JSON válido: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildJsonSchema() {
        ObjectNode lineaItem = MAPPER.createObjectNode();
        lineaItem.put("type", "object");
        lineaItem.put("additionalProperties", false);
        ObjectNode p = lineaItem.putObject("properties");
        p.putObject("fotoIndice").put("type", "integer");
        nullableString(p, "proveedor");
        p.putObject("estacion").put("type", "string");
        nullableString(p, "direccion");
        p.putObject("fecha").put("type", "string");
        p.putObject("hora").put("type", "string");
        nullableString(p, "numTarjeta4ultimos");
        nullableString(p, "matricula");
        nullableNumber(p, "kms");
        p.putObject("producto").put("type", "string");
        p.putObject("litros").put("type", "number");
        p.putObject("precioLitro").put("type", "number");
        p.putObject("importeTotal").put("type", "number");
        nullableString(p, "numRecibo");
        nullableString(p, "nifEstacion");
        lineaItem.putArray("required").add("fotoIndice").add("proveedor").add("estacion").add("direccion")
                .add("fecha").add("hora").add("numTarjeta4ultimos").add("matricula").add("kms")
                .add("producto").add("litros").add("precioLitro").add("importeTotal")
                .add("numRecibo").add("nifEstacion");

        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode props = schema.putObject("properties");
        ObjectNode lineasArray = props.putObject("lineas");
        lineasArray.put("type", "array");
        lineasArray.set("items", lineaItem);
        schema.putArray("required").add("lineas");

        ObjectNode jsonSchemaWrapper = MAPPER.createObjectNode();
        jsonSchemaWrapper.put("name", "ticket_extraido");
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

    private void nullableNumber(ObjectNode props, String name) {
        ObjectNode node = props.putObject(name);
        node.putArray("type").add("number").add("null");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAPEO JSON → LineaTicketExtraida
    // ─────────────────────────────────────────────────────────────────────────

    List<LineaTicketExtraida> construirResultado(JsonNode raiz) {
        List<LineaTicketExtraida> lineas = new ArrayList<>();
        for (JsonNode l : raiz.path("lineas")) {
            lineas.add(new LineaTicketExtraida(
                    l.path("fotoIndice").asInt(0),
                    text(l, "proveedor"),
                    text(l, "estacion"),
                    text(l, "direccion"),
                    fechaHora(l),
                    text(l, "numTarjeta4ultimos"),
                    text(l, "matricula"),
                    integer(l, "kms"),
                    text(l, "producto"),
                    decimal(l, "litros"),
                    decimal(l, "precioLitro"),
                    decimal(l, "importeTotal"),
                    text(l, "numRecibo"),
                    text(l, "nifEstacion")
            ));
        }
        return lineas;
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.decimalValue();
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asInt();
    }

    private LocalDateTime fechaHora(JsonNode node) {
        String fecha = text(node, "fecha");
        String hora = text(node, "hora");
        if (fecha == null || fecha.isBlank()) {
            return null;
        }
        LocalDate d = LocalDate.parse(fecha);
        if (hora == null || hora.isBlank()) {
            return d.atStartOfDay();
        }
        String[] hm = hora.split(":");
        int h = Integer.parseInt(hm[0]);
        int m = hm.length > 1 ? Integer.parseInt(hm[1]) : 0;
        return d.atTime(h, m);
    }
}
