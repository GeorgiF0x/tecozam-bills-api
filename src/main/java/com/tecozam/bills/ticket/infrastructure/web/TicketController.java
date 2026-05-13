package com.tecozam.bills.ticket.infrastructure.web;

import com.tecozam.bills.ticket.application.TicketService;
import com.tecozam.bills.ticket.dto.CotejoResultDTO;
import com.tecozam.bills.ticket.dto.CreateTicketManualRequest;
import com.tecozam.bills.ticket.dto.CreateTicketOcrValidadoRequest;
import com.tecozam.bills.ticket.dto.TicketDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "Gestión de tickets de combustible y gastos")
public class TicketController {

    private final TicketService ticketService;

    @Value("${app.n8n.webhook-url:http://localhost:5678/webhook/tecozam}")
    private String n8nWebhookBaseUrl;

    @GetMapping
    @Operation(summary = "Listar tickets", description = "Obtiene todos los tickets, opcionalmente filtrados por estado de cotejo")
    public ResponseEntity<List<TicketDTO>> findAll(
            @RequestParam(required = false) String estadoCotejo) {
        return ResponseEntity.ok(ticketService.findAll(estadoCotejo));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener ticket", description = "Obtiene un ticket por su ID")
    public ResponseEntity<TicketDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    @Operation(summary = "Crear ticket manual", description = "Crea un nuevo ticket de forma manual")
    public ResponseEntity<TicketDTO> createManual(@Valid @RequestBody CreateTicketManualRequest request) {
        TicketDTO created = ticketService.createManual(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/cotejar")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    @Operation(summary = "Cotejar tickets pendientes", description = "Procesa todos los tickets pendientes y los coteja con operaciones de facturas")
    public ResponseEntity<CotejoResultDTO> cotejarPendientes() {
        return ResponseEntity.ok(ticketService.cotejarPendientes());
    }

    @PatchMapping("/{id}/incidencia")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    @Operation(summary = "Marcar incidencia", description = "Marca un ticket como incidencia con observaciones, tipo y asignación")
    public ResponseEntity<TicketDTO> marcarIncidencia(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String observaciones = body != null ? body.get("observaciones") : null;
        String tipoIncidencia = body != null ? body.get("tipoIncidencia") : null;
        String asignadoAIdStr = body != null ? body.get("asignadoAId") : null;
        TicketDTO updated = ticketService.marcarIncidencia(id, observaciones, tipoIncidencia,
                asignadoAIdStr != null ? Long.parseLong(asignadoAIdStr) : null);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/vincular/{operacionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    @Operation(summary = "Vincular operación manualmente", description = "Vincula un ticket a una operación específica y lo marca como COTEJADO")
    public ResponseEntity<TicketDTO> vincularOperacion(@PathVariable Long id, @PathVariable Long operacionId) {
        TicketDTO updated = ticketService.vincularOperacion(id, operacionId);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}/resolver")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    @Operation(summary = "Resolver incidencia", description = "Registra la resolución de una incidencia con notas y timestamp")
    public ResponseEntity<TicketDTO> resolverIncidencia(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String notas = body != null ? body.get("notasResolucion") : null;
        TicketDTO updated = ticketService.resolverIncidencia(id, notas);
        return ResponseEntity.ok(updated);
    }

    @Value("${app.openai.api-key:}")
    private String openaiApiKey;

    @PostMapping("/ocr")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR', 'CONSULTA')")
    @Operation(summary = "OCR de ticket", description = "Extrae datos de un ticket vía OpenAI Vision y guarda el ticket localmente")
    public ResponseEntity<Map<String, Object>> ocrTicket(@RequestParam("imagen") MultipartFile imagen) {
        try {
            if (imagen == null || imagen.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "La imagen es obligatoria"));
            }
            if (openaiApiKey == null || openaiApiKey.isBlank()) {
                return ResponseEntity.internalServerError().body(Map.of("error", "OCR no configurado: falta OPENAI_API_KEY"));
            }

            String base64 = Base64.getEncoder().encodeToString(imagen.getBytes());
            String mimeType = imagen.getContentType() != null ? imagen.getContentType() : "image/jpeg";

            // Call OpenAI Vision API directly
            String prompt = "Extrae los datos de este ticket de gasolinera o peaje español. " +
                    "Devuelve SOLO un JSON válido con estos campos exactos: " +
                    "{ \"proveedor\": \"REPSOL o MOEVE_CEPSA u otro\", " +
                    "\"estacion\": \"nombre de la estación\", " +
                    "\"direccion\": \"dirección completa o null\", " +
                    "\"fecha\": \"YYYY-MM-DD\", " +
                    "\"hora\": \"HH:MM\", " +
                    "\"numTarjeta4ultimos\": \"últimos 4 dígitos o null\", " +
                    "\"matricula\": \"matrícula o null\", " +
                    "\"kms\": número o null, " +
                    "\"producto\": \"tipo de combustible\", " +
                    "\"litros\": número con decimales, " +
                    "\"precioLitro\": número con 3 decimales, " +
                    "\"importeTotal\": número con 2 decimales, " +
                    "\"numRecibo\": \"número de ticket/recibo o null\", " +
                    "\"nifEstacion\": \"NIF o null\" }. " +
                    "Usa punto como separador decimal. Si un campo no es legible, pon null. " +
                    "NO incluyas texto adicional, SOLO el JSON.";

            String requestBody = "{" +
                    "\"model\":\"gpt-4.1-mini\"," +
                    "\"messages\":[{\"role\":\"user\",\"content\":[" +
                    "{\"type\":\"text\",\"text\":\"" + escapeJson(prompt) + "\"}," +
                    "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:" + mimeType + ";base64," + base64 + "\"}}" +
                    "]}]," +
                    "\"max_tokens\":800," +
                    "\"temperature\":0" +
                    "}";

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[OCR] OpenAI status: {}", response.statusCode());

            if (response.statusCode() != 200) {
                log.error("[OCR] OpenAI error: {}", response.body());
                return ResponseEntity.status(422).body(Map.of(
                        "error", "Error al procesar imagen con IA",
                        "detail", response.body()
                ));
            }

            // Extract content from OpenAI response
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var aiResponse = mapper.readTree(response.body());
            String content = aiResponse.at("/choices/0/message/content").asText("");
            log.info("[OCR] AI content: {}", content);

            // Clean the content — remove markdown code fences if present
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            // Parse the JSON
            var data = mapper.readTree(content);

            String estacion = getField(data, "estacion");
            String fechaStr = getField(data, "fecha");
            String horaStr = getFieldOr(data, "hora", "00:00");
            double importeTotal = getNumField(data, "importeTotal", "importe_total", "total");
            double litros = getNumField(data, "litros", "cantidad");
            double precioLitro = getNumField(data, "precioLitro", "precio_litro", "precio");
            String producto = getFieldAny(data, "producto", "concepto", "combustible");
            String numRecibo = getFieldAny(data, "numRecibo", "num_recibo", "referencia");
            int kms = (int) getNumField(data, "kms", "kilometros");

            // Build fechaHora
            java.time.LocalDateTime fechaHora;
            try {
                java.time.LocalDate fecha = java.time.LocalDate.parse(fechaStr);
                String[] hm = horaStr.split(":");
                fechaHora = fecha.atTime(Integer.parseInt(hm[0]), Integer.parseInt(hm.length > 1 ? hm[1] : "0"));
            } catch (Exception e) {
                fechaHora = java.time.LocalDateTime.now();
            }

            // Create ticket
            var ticketRequest = new CreateTicketManualRequest(
                    null, null, null, null,
                    estacion.isBlank() ? "OCR" : estacion,
                    fechaHora,
                    java.math.BigDecimal.valueOf(importeTotal > 0 ? importeTotal : 0.01),
                    litros > 0 ? java.math.BigDecimal.valueOf(litros) : null,
                    precioLitro > 0 ? java.math.BigDecimal.valueOf(precioLitro) : null,
                    kms > 0 ? kms : null,
                    producto.isBlank() ? null : producto,
                    "OCR automático (OpenAI Vision)" + (numRecibo != null && !numRecibo.isEmpty() ? " — Recibo: " + numRecibo : "")
            );

            TicketDTO savedTicket = ticketService.createManual(ticketRequest);

            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "Ticket creado: " + estacion + " — " + importeTotal + "€ — " + litros + "L",
                    "ticketId", savedTicket.id(),
                    "ocrData", content
            ));
        } catch (Exception e) {
            log.error("[OCR] Exception: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Error al procesar OCR: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/ocr-preview")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Preview OCR de ticket con autenticación PIN",
            description = "Valida PIN y asignación de tarjeta. Si OK, extrae datos del ticket vía OpenAI Vision SIN crear ticket. Para mostrar al usuario los datos antes de la edición final.")
    public ResponseEntity<Map<String, Object>> ocrPreview(
            @RequestParam("imagen") MultipartFile imagen,
            @RequestParam("tarjetaId") Long tarjetaId,
            @RequestParam("pin") String pin,
            Authentication authentication) {
        try {
            if (imagen == null || imagen.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "La imagen es obligatoria"));
            }
            if (openaiApiKey == null || openaiApiKey.isBlank()) {
                return ResponseEntity.internalServerError()
                        .body(Map.of("error", "OCR no configurado: falta OPENAI_API_KEY"));
            }

            // ── Validar PIN + asignación ANTES de hacer OCR ──────────────
            try {
                ticketService.validarPinYAsignacion(
                        authentication.getName(), tarjetaId, pin);
            } catch (com.tecozam.bills.shared.infrastructure.exception.BusinessException be) {
                if ("PIN incorrecto".equals(be.getMessage())) {
                    return ResponseEntity.status(401).body(Map.of("error", "PIN incorrecto"));
                }
                return ResponseEntity.badRequest().body(Map.of("error", be.getMessage()));
            }

            OcrExtraction extracted = performOcr(imagen);
            if (extracted.errorBody != null) {
                return ResponseEntity.status(extracted.errorStatus).body(extracted.errorBody);
            }

            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("estacion", extracted.estacion);
            response.put("fechaHora", extracted.fechaHora.toString());
            response.put("importeTotal", extracted.importeTotal);
            response.put("litros", extracted.litros);
            response.put("precioLitro", extracted.precioLitro);
            response.put("producto", extracted.producto);
            response.put("numRecibo", extracted.numRecibo);
            response.put("matricula", extracted.matricula);
            response.put("kms", extracted.kms);
            response.put("ocrRaw", extracted.ocrRaw);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[OCR-PREVIEW] Exception: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Error al procesar preview OCR: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/ocr-validado")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "OCR validado con PIN",
            description = "Crea un ticket con OCR_VALIDADO. Si en params vienen los datos OCR (estacion, fechaHora, importeTotal...) los usa directamente sin llamar a OpenAI. Si no, hace OCR de fallback con la imagen.")
    public ResponseEntity<Map<String, Object>> ocrValidado(
            @RequestParam("imagen") MultipartFile imagen,
            @RequestPart("params") @Valid CreateTicketOcrValidadoRequest params,
            Authentication authentication) {
        try {
            if (imagen == null || imagen.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "La imagen es obligatoria"));
            }

            // ── Datos OCR: o vienen del cliente (preview previo) o se extraen ──
            String estacion;
            java.time.LocalDateTime fechaHora;
            java.math.BigDecimal importeTotal;
            java.math.BigDecimal litros;
            java.math.BigDecimal precioLitro;
            String producto;
            String numRecibo;
            String ocrRaw;

            boolean clientProvidedData =
                    params.estacion() != null && params.importeTotal() != null;

            if (clientProvidedData) {
                // Modo nuevo: cliente ya hizo el preview, usar sus datos
                log.info("[OCR-VALIDADO] Usando datos del cliente (preview previo)");
                estacion = params.estacion();
                fechaHora = params.fechaHora() != null ? params.fechaHora() : java.time.LocalDateTime.now();
                importeTotal = params.importeTotal();
                litros = params.litros();
                precioLitro = params.precioLitro();
                producto = params.producto();
                numRecibo = params.numRecibo();
                ocrRaw = params.ocrRaw();
            } else {
                // Modo legacy: hacer OCR ahora
                if (openaiApiKey == null || openaiApiKey.isBlank()) {
                    return ResponseEntity.internalServerError()
                            .body(Map.of("error", "OCR no configurado: falta OPENAI_API_KEY"));
                }
                log.info("[OCR-VALIDADO] Cliente no envió datos, haciendo OCR fallback");
                OcrExtraction ex = performOcr(imagen);
                if (ex.errorBody != null) {
                    return ResponseEntity.status(ex.errorStatus).body(ex.errorBody);
                }
                estacion = ex.estacion;
                fechaHora = ex.fechaHora;
                importeTotal = java.math.BigDecimal.valueOf(ex.importeTotal > 0 ? ex.importeTotal : 0.01);
                litros = ex.litros > 0 ? java.math.BigDecimal.valueOf(ex.litros) : null;
                precioLitro = ex.precioLitro > 0 ? java.math.BigDecimal.valueOf(ex.precioLitro) : null;
                producto = ex.producto.isBlank() ? null : ex.producto;
                numRecibo = ex.numRecibo.isBlank() ? null : ex.numRecibo;
                ocrRaw = ex.ocrRaw;
            }

            // ── Validar PIN y crear ticket ─────────────────────────────
            try {
                TicketDTO ticket = ticketService.createOcrValidado(
                        authentication.getName(),
                        params,
                        estacion,
                        fechaHora,
                        importeTotal,
                        litros,
                        precioLitro,
                        producto,
                        numRecibo
                );

                Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("status", "ok");
                response.put("ticketId", ticket.id());
                response.put("message", "Ticket creado: " + estacion + " — " + importeTotal + "€");
                response.put("ocrData", ocrRaw);
                return ResponseEntity.ok(response);
            } catch (com.tecozam.bills.shared.infrastructure.exception.BusinessException be) {
                if ("PIN incorrecto".equals(be.getMessage())) {
                    return ResponseEntity.status(401).body(Map.of("error", "PIN incorrecto"));
                }
                return ResponseEntity.badRequest().body(Map.of("error", be.getMessage()));
            }

        } catch (Exception e) {
            log.error("[OCR-VALIDADO] Exception: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Error al procesar OCR validado: " + e.getMessage()
            ));
        }
    }

    /**
     * Estructura interna con datos extraídos del OCR (helper para
     * compartir lógica entre /ocr-preview y /ocr-validado fallback).
     */
    private static class OcrExtraction {
        String estacion = "";
        java.time.LocalDateTime fechaHora;
        double importeTotal;
        double litros;
        double precioLitro;
        String producto = "";
        String numRecibo = "";
        String matricula = "";
        int kms;
        String ocrRaw = "";
        // Error fields (si se setean, abortar)
        Integer errorStatus;
        Map<String, Object> errorBody;
    }

    /**
     * Llama a OpenAI Vision para extraer datos del ticket.
     * Lógica compartida entre /ocr-preview y /ocr-validado (fallback).
     */
    private OcrExtraction performOcr(MultipartFile imagen) throws Exception {
        OcrExtraction result = new OcrExtraction();

        String base64 = Base64.getEncoder().encodeToString(imagen.getBytes());
        String mimeType = imagen.getContentType() != null ? imagen.getContentType() : "image/jpeg";

        String prompt = "Extrae los datos de este ticket de gasolinera o peaje español. " +
                "Devuelve SOLO un JSON válido con estos campos exactos: " +
                "{ \"proveedor\": \"REPSOL o MOEVE_CEPSA u otro\", " +
                "\"estacion\": \"nombre de la estación\", " +
                "\"direccion\": \"dirección completa o null\", " +
                "\"fecha\": \"YYYY-MM-DD\", " +
                "\"hora\": \"HH:MM\", " +
                "\"numTarjeta4ultimos\": \"últimos 4 dígitos o null\", " +
                "\"matricula\": \"matrícula o null\", " +
                "\"kms\": número o null, " +
                "\"producto\": \"tipo de combustible\", " +
                "\"litros\": número con decimales, " +
                "\"precioLitro\": número con 3 decimales, " +
                "\"importeTotal\": número con 2 decimales, " +
                "\"numRecibo\": \"número de ticket/recibo o null\", " +
                "\"nifEstacion\": \"NIF o null\" }. " +
                "Usa punto como separador decimal. Si un campo no es legible, pon null. " +
                "NO incluyas texto adicional, SOLO el JSON.";

        String requestBody = "{" +
                "\"model\":\"gpt-4.1-mini\"," +
                "\"messages\":[{\"role\":\"user\",\"content\":[" +
                "{\"type\":\"text\",\"text\":\"" + escapeJson(prompt) + "\"}," +
                "{\"type\":\"image_url\",\"image_url\":{\"url\":\"data:" + mimeType + ";base64," + base64 + "\"}}" +
                "]}]," +
                "\"max_tokens\":800," +
                "\"temperature\":0" +
                "}";

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openaiApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        log.info("[OCR] OpenAI status: {}", response.statusCode());

        if (response.statusCode() != 200) {
            log.error("[OCR] OpenAI error: {}", response.body());
            result.errorStatus = 422;
            result.errorBody = Map.of("error", "Error al procesar imagen con IA", "detail", response.body());
            return result;
        }

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var aiResponse = mapper.readTree(response.body());
        String content = aiResponse.at("/choices/0/message/content").asText("");
        content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        result.ocrRaw = content;
        log.info("[OCR] AI content: {}", content);

        var data = mapper.readTree(content);
        result.estacion = getField(data, "estacion");
        String fechaStr = getField(data, "fecha");
        String horaStr = getFieldOr(data, "hora", "00:00");
        result.importeTotal = getNumField(data, "importeTotal", "importe_total", "total");
        result.litros = getNumField(data, "litros", "cantidad");
        result.precioLitro = getNumField(data, "precioLitro", "precio_litro", "precio");
        result.producto = getFieldAny(data, "producto", "concepto", "combustible");
        result.numRecibo = getFieldAny(data, "numRecibo", "num_recibo", "referencia");
        result.matricula = getFieldAny(data, "matricula");
        result.kms = (int) getNumField(data, "kms", "kilometros");

        try {
            java.time.LocalDate fecha = java.time.LocalDate.parse(fechaStr);
            String[] hm = horaStr.split(":");
            result.fechaHora = fecha.atTime(Integer.parseInt(hm[0]), Integer.parseInt(hm.length > 1 ? hm[1] : "0"));
        } catch (Exception e) {
            result.fechaHora = java.time.LocalDateTime.now();
        }
        return result;
    }

    private String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    // ─── OCR field extraction helpers ─────────────────────────────────────────

    private static String getField(com.fasterxml.jackson.databind.JsonNode data, String key) {
        return data.has(key) && !data.get(key).isNull() ? data.get(key).asText("") : "";
    }

    private static String getFieldOr(com.fasterxml.jackson.databind.JsonNode data, String key, String defaultValue) {
        String val = getField(data, key);
        return val.isEmpty() ? defaultValue : val;
    }

    private static String getFieldAny(com.fasterxml.jackson.databind.JsonNode data, String... keys) {
        for (String key : keys) {
            String val = getField(data, key);
            if (!val.isEmpty()) return val;
        }
        return "";
    }

    private static double getNumField(com.fasterxml.jackson.databind.JsonNode data, String... keys) {
        for (String key : keys) {
            if (data.has(key) && !data.get(key).isNull()) {
                try {
                    return data.get(key).asDouble(0);
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TicketController.class);
}
