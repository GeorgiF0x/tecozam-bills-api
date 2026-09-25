package com.tecozam.bills.factura.infrastructure.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecozam.bills.factura.domain.Factura;
import com.tecozam.bills.factura.domain.FacturaConceptoResumen;
import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.domain.TarjetaResumen;
import com.tecozam.bills.shared.domain.enums.ConceptoUnificado;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extrae los campos de una factura PDF (+ extracto opcional) usando un LLM en
 * vez del parser determinista de siempre. Solo se usa cuando el admin activa
 * el modo LLM (ver odd/tasks/import-llm-switch.md); el resultado SIEMPRE pasa
 * despues por {@link com.tecozam.bills.factura.application.FacturaImportValidator}
 * antes de entrar al cotejo automatico — un fallo de extraccion aqui no debe
 * corromper el cotejo en silencio, solo generar avisos que bloqueen la
 * factura para revision manual.
 *
 * No es un {@code @Component} singleton: se instancia por importacion via
 * {@link LlmFacturaParserFactory} porque necesita saber el proveedor
 * (Repsol/Moeve) para construir el prompt, y la interfaz {@link FacturaParser}
 * no lo recibe como parametro.
 */
@Slf4j
public class LlmFacturaParser implements FacturaParser {

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String codigoProveedor;
    private final String apiKey;
    private final String model;

    public LlmFacturaParser(String codigoProveedor, String apiKey, String model) {
        this.codigoProveedor = codigoProveedor;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public FacturaParseResult parse(InputStream pdfInputStream, InputStream extractoInputStream) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "El modo LLM de import está activado pero falta la clave OPENAI_API_KEY.");
        }

        String textoFactura = extraerTexto(pdfInputStream);
        String textoExtracto = extractoInputStream != null ? extraerTexto(extractoInputStream) : null;

        String respuestaJson = callOpenAI(textoFactura, textoExtracto);
        JsonNode raiz = parsearJson(respuestaJson);

        return construirResultado(raiz);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXTRACCIÓN DE TEXTO PDF (idéntico al patrón de RepsolFacturaParser)
    // ─────────────────────────────────────────────────────────────────────────

    private String extraerTexto(InputStream pdfInputStream) throws Exception {
        byte[] bytes = pdfInputStream.readAllBytes();
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LLAMADA A OPENAI (modo JSON estructurado)
    // ─────────────────────────────────────────────────────────────────────────

    private String callOpenAI(String textoFactura, String textoExtracto) throws Exception {
        String systemPrompt = "Eres un extractor de datos de facturas de combustible de flota (proveedor: "
                + codigoProveedor + "). Extrae ÚNICAMENTE los datos que aparezcan literalmente en el texto. "
                + "No inventes ni calcules valores que no estén presentes. Fechas en formato yyyy-MM-dd. "
                + "Fechas y horas de operaciones en formato yyyy-MM-ddTHH:mm:ss. "
                + "Para 'conceptoUnificado' de cada concepto/operación, clasifica el texto original a "
                + "exactamente uno de estos valores: " + Arrays.stream(ConceptoUnificado.values())
                        .map(Enum::name).collect(Collectors.joining(", ")) + ".";

        StringBuilder userContent = new StringBuilder();
        userContent.append("=== TEXTO DE LA FACTURA ===\n").append(textoFactura);
        if (textoExtracto != null && !textoExtracto.isBlank()) {
            userContent.append("\n\n=== TEXTO DEL EXTRACTO ===\n").append(textoExtracto);
        }

        ObjectNode requestBody = MAPPER.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.0);

        ArrayNode messages = requestBody.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userContent.toString());

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
            log.error("[LlmFacturaParser] OpenAI devolvió {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Error del servicio de IA al extraer la factura (HTTP " + response.statusCode() + ")");
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

    /**
     * Define el schema JSON estructurado (modo {@code json_schema} de OpenAI)
     * con los campos de {@link Factura}/{@link TarjetaResumen}/{@link Operacion}/
     * {@link FacturaConceptoResumen} que tienen sentido extraer de un PDF.
     * Campos secundarios raramente usados por el cotejo (kms, dto%, bonificación,
     * observaciones) se omiten deliberadamente para mantener el prompt enfocado.
     */
    private ObjectNode buildJsonSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        ObjectNode props = schema.putObject("properties");

        props.putObject("numFactura").put("type", "string");
        props.putObject("fecha").put("type", "string");
        props.putObject("periodoDesde").put("type", "string");
        props.putObject("periodoHasta").put("type", "string");
        nullableString(props, "vencimiento");
        nullableString(props, "numCuenta");
        nullableString(props, "nifCliente");
        nullableString(props, "iban");
        props.putObject("baseImponible").put("type", "number");
        props.putObject("totalIva").put("type", "number");
        props.putObject("totalFactura").put("type", "number");

        ObjectNode conceptoItem = MAPPER.createObjectNode();
        conceptoItem.put("type", "object");
        conceptoItem.put("additionalProperties", false);
        ObjectNode conceptoProps = conceptoItem.putObject("properties");
        conceptoProps.putObject("conceptoOriginal").put("type", "string");
        conceptoProps.set("conceptoUnificado", conceptoUnificadoEnum());
        nullableNumber(conceptoProps, "cantidad");
        conceptoProps.putObject("baseImponible").put("type", "number");
        conceptoProps.putObject("tipoIva").put("type", "number");
        conceptoProps.putObject("cuotaIva").put("type", "number");
        conceptoProps.putObject("importe").put("type", "number");
        conceptoItem.putArray("required").add("conceptoOriginal").add("conceptoUnificado")
                .add("cantidad").add("baseImponible").add("tipoIva").add("cuotaIva").add("importe");
        ObjectNode conceptosArray = props.putObject("conceptos");
        conceptosArray.put("type", "array");
        conceptosArray.set("items", conceptoItem);

        ObjectNode operacionItem = MAPPER.createObjectNode();
        operacionItem.put("type", "object");
        operacionItem.put("additionalProperties", false);
        ObjectNode opProps = operacionItem.putObject("properties");
        nullableString(opProps, "referencia");
        opProps.putObject("fechaHora").put("type", "string");
        opProps.putObject("conceptoOriginal").put("type", "string");
        opProps.set("conceptoUnificado", conceptoUnificadoEnum());
        nullableString(opProps, "establecimiento");
        nullableNumber(opProps, "cantidad");
        nullableNumber(opProps, "precioUnitario");
        opProps.putObject("importeTotal").put("type", "number");
        operacionItem.putArray("required").add("referencia").add("fechaHora").add("conceptoOriginal")
                .add("conceptoUnificado").add("establecimiento").add("cantidad").add("precioUnitario")
                .add("importeTotal");

        ObjectNode tarjetaItem = MAPPER.createObjectNode();
        tarjetaItem.put("type", "object");
        tarjetaItem.put("additionalProperties", false);
        ObjectNode tarjetaProps = tarjetaItem.putObject("properties");
        tarjetaProps.putObject("numTarjeta").put("type", "string");
        nullableString(tarjetaProps, "alias");
        nullableString(tarjetaProps, "conductor");
        ObjectNode operacionesArray = tarjetaProps.putObject("operaciones");
        operacionesArray.put("type", "array");
        operacionesArray.set("items", operacionItem);
        tarjetaItem.putArray("required").add("numTarjeta").add("alias").add("conductor").add("operaciones");
        ObjectNode tarjetasArray = props.putObject("tarjetas");
        tarjetasArray.put("type", "array");
        tarjetasArray.set("items", tarjetaItem);

        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putArray("required").add("numFactura").add("fecha").add("periodoDesde").add("periodoHasta")
                .add("vencimiento").add("numCuenta").add("nifCliente").add("iban")
                .add("baseImponible").add("totalIva").add("totalFactura").add("conceptos").add("tarjetas");

        ObjectNode jsonSchemaWrapper = MAPPER.createObjectNode();
        jsonSchemaWrapper.put("name", "factura_extraida");
        jsonSchemaWrapper.put("strict", true);
        jsonSchemaWrapper.set("schema", schema);

        ObjectNode responseFormat = MAPPER.createObjectNode();
        responseFormat.put("type", "json_schema");
        responseFormat.set("json_schema", jsonSchemaWrapper);
        return responseFormat;
    }

    private ObjectNode conceptoUnificadoEnum() {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "string");
        ArrayNode enumValues = node.putArray("enum");
        for (ConceptoUnificado c : ConceptoUnificado.values()) {
            enumValues.add(c.name());
        }
        return node;
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
    // MAPEO JSON → ENTIDADES
    // ─────────────────────────────────────────────────────────────────────────

    FacturaParseResult construirResultado(JsonNode raiz) {
        Factura factura = Factura.builder()
                .numFactura(text(raiz, "numFactura"))
                .fecha(date(raiz, "fecha"))
                .periodoDesde(date(raiz, "periodoDesde"))
                .periodoHasta(date(raiz, "periodoHasta"))
                .vencimiento(date(raiz, "vencimiento"))
                .numCuenta(text(raiz, "numCuenta"))
                .nifCliente(text(raiz, "nifCliente"))
                .iban(text(raiz, "iban"))
                .baseImponible(decimal(raiz, "baseImponible"))
                .totalIva(decimal(raiz, "totalIva"))
                .totalFactura(decimal(raiz, "totalFactura"))
                .build();

        List<FacturaConceptoResumen> conceptos = new ArrayList<>();
        for (JsonNode c : raiz.path("conceptos")) {
            conceptos.add(FacturaConceptoResumen.builder()
                    .conceptoOriginal(text(c, "conceptoOriginal"))
                    .conceptoUnificado(text(c, "conceptoUnificado"))
                    .cantidad(decimal(c, "cantidad"))
                    .baseImponible(decimal(c, "baseImponible"))
                    .tipoIva(decimal(c, "tipoIva"))
                    .cuotaIva(decimal(c, "cuotaIva"))
                    .importe(decimal(c, "importe"))
                    .build());
        }

        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();
        for (JsonNode t : raiz.path("tarjetas")) {
            TarjetaResumen tr = TarjetaResumen.builder()
                    .numTarjeta(text(t, "numTarjeta"))
                    .alias(text(t, "alias"))
                    .conductor(text(t, "conductor"))
                    .operaciones(new ArrayList<>())
                    .build();

            for (JsonNode o : t.path("operaciones")) {
                Operacion op = Operacion.builder()
                        .referencia(text(o, "referencia"))
                        .fechaHora(dateTime(o, "fechaHora"))
                        .conceptoOriginal(text(o, "conceptoOriginal"))
                        .conceptoUnificado(text(o, "conceptoUnificado"))
                        .establecimiento(text(o, "establecimiento"))
                        .cantidad(decimal(o, "cantidad"))
                        .precioUnitario(decimal(o, "precioUnitario"))
                        .importeTotal(decimal(o, "importeTotal"))
                        .tarjetaResumen(tr)
                        .build();
                tr.getOperaciones().add(op);
            }
            tarjetaResumenes.add(tr);
        }

        conceptos.forEach(c -> c.setFactura(factura));
        factura.setConceptos(conceptos);

        tarjetaResumenes.forEach(tr -> {
            tr.setFactura(factura);
            tr.getOperaciones().forEach(op -> op.setFactura(factura));
        });
        factura.setTarjetaResumenes(tarjetaResumenes);

        return FacturaParseResult.builder()
                .factura(factura)
                .conceptos(conceptos)
                .tarjetaResumenes(tarjetaResumenes)
                .build();
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.decimalValue();
    }

    private LocalDate date(JsonNode node, String field) {
        String s = text(node, field);
        return s == null ? null : LocalDate.parse(s);
    }

    private LocalDateTime dateTime(JsonNode node, String field) {
        String s = text(node, field);
        return s == null ? null : LocalDateTime.parse(s);
    }
}
