package com.tecozam.bills.ticket.infrastructure.web;

import com.tecozam.bills.ticket.application.TicketService;
import com.tecozam.bills.ticket.dto.CotejoResultDTO;
import com.tecozam.bills.ticket.dto.CreateTicketManualRequest;
import com.tecozam.bills.ticket.dto.TicketDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @PostMapping("/ocr")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    @Operation(summary = "OCR de ticket", description = "Envía una imagen de ticket a n8n para OCR y guarda el ticket localmente")
    public ResponseEntity<Map<String, Object>> ocrTicket(@RequestParam("imagen") MultipartFile imagen) {
        try {
            if (imagen == null || imagen.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "La imagen es obligatoria"));
            }

            String base64 = Base64.getEncoder().encodeToString(imagen.getBytes());

            // Build the n8n webhook URL
            String webhookUrl = n8nWebhookBaseUrl;
            if (!webhookUrl.contains("ocr-ticket")) {
                webhookUrl = webhookUrl.replaceAll("/webhook/.*$", "/webhook/ocr-ticket");
            }

            String requestBody = "{\"imagen\":\"" + base64 + "\"}";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return ResponseEntity.status(response.statusCode()).body(Map.of(
                        "error", "Error en n8n OCR",
                        "detail", response.body()
                ));
            }

            // Parse n8n response and create ticket locally
            String n8nBody = response.body();
            log.info("[OCR] n8n raw response: {}", n8nBody);
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var json = mapper.readTree(n8nBody);

                // n8n may return the OCR data directly or wrapped in a "data" field
                var data = json.has("data") ? json.get("data") : json;

                // Try to find the actual OCR JSON — n8n sometimes nests it
                if (data.has("output") && data.get("output").isTextual()) {
                    try { data = mapper.readTree(data.get("output").asText()); } catch (Exception ignored) {}
                }
                if (data.has("message") && data.get("message").isObject()) {
                    data = data.get("message");
                }
                if (data.has("content") && data.get("content").isTextual()) {
                    try { data = mapper.readTree(data.get("content").asText()); } catch (Exception ignored) {}
                }

                log.info("[OCR] Parsed data fields: {}", data.fieldNames());

                String estacion = getField(data, "estacion");
                String fechaStr = getField(data, "fecha");
                String horaStr = getFieldOr(data, "hora", "00:00");
                double importeTotal = getNumField(data, "importeTotal", "importe_total", "total", "importe");
                double litros = getNumField(data, "litros", "cantidad");
                double precioLitro = getNumField(data, "precioLitro", "precio_litro", "precio");
                String producto = getFieldAny(data, "producto", "concepto", "combustible");
                String numRecibo = getFieldAny(data, "numRecibo", "num_recibo", "referencia", "ticket");

                // Build fechaHora
                java.time.LocalDateTime fechaHora;
                try {
                    java.time.LocalDate fecha = java.time.LocalDate.parse(fechaStr);
                    String[] hm = horaStr.split(":");
                    fechaHora = fecha.atTime(Integer.parseInt(hm[0]), Integer.parseInt(hm.length > 1 ? hm[1] : "0"));
                } catch (Exception e) {
                    fechaHora = java.time.LocalDateTime.now();
                }

                // Create ticket via service
                var ticketRequest = new CreateTicketManualRequest(
                        null, null, null, null,
                        estacion.isBlank() ? "OCR" : estacion,
                        fechaHora,
                        java.math.BigDecimal.valueOf(importeTotal > 0 ? importeTotal : 0.01),
                        litros > 0 ? java.math.BigDecimal.valueOf(litros) : null,
                        precioLitro > 0 ? java.math.BigDecimal.valueOf(precioLitro) : null,
                        null,
                        producto.isBlank() ? null : producto,
                        "OCR automático" + (numRecibo != null ? " — Recibo: " + numRecibo : "")
                );

                TicketDTO savedTicket = ticketService.createManual(ticketRequest);

                return ResponseEntity.ok(Map.of(
                        "status", "ok",
                        "message", "Ticket creado desde OCR: " + estacion + " — " + importeTotal + "€",
                        "ticketId", savedTicket.id(),
                        "ocrData", n8nBody
                ));
            } catch (Exception parseEx) {
                // n8n responded OK but we couldn't parse — return raw data
                return ResponseEntity.ok(Map.of(
                        "status", "partial",
                        "message", "OCR procesado pero no se pudo crear el ticket automáticamente. Datos: " + n8nBody,
                        "ocrRaw", n8nBody
                ));
            }
        } catch (Exception e) {
            log.error("[OCR] Exception: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Error al procesar OCR: " + e.getMessage()
            ));
        }
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
