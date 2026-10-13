package com.tecozam.bills.ticket.infrastructure.web;

import com.tecozam.bills.ticket.application.TicketService;
import com.tecozam.bills.ticket.dto.CotejoResultDTO;
import com.tecozam.bills.ticket.dto.CreateTicketManualRequest;
import com.tecozam.bills.ticket.dto.CreateTicketOcrValidadoRequest;
import com.tecozam.bills.ticket.dto.LineaOcrDTO;
import com.tecozam.bills.ticket.dto.TicketDTO;
import com.tecozam.bills.ticket.infrastructure.parser.ImagenTicket;
import com.tecozam.bills.ticket.infrastructure.parser.LineaTicketExtraida;
import com.tecozam.bills.ticket.infrastructure.parser.LlmTicketParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "Gestión de tickets de combustible y gastos")
public class TicketController {

    private final TicketService ticketService;
    private final LlmTicketParser llmTicketParser;

    @org.springframework.beans.factory.annotation.Value("${app.n8n.webhook-url:http://localhost:5678/webhook/tecozam}")
    private String n8nWebhookBaseUrl;

    @GetMapping
    @Operation(summary = "Listar tickets", description = "Obtiene todos los tickets, opcionalmente filtrados por estado de cotejo")
    public ResponseEntity<List<TicketDTO>> findAll(
            @RequestParam(required = false) String estadoCotejo) {
        return ResponseEntity.ok(ticketService.findAll(estadoCotejo));
    }

    @GetMapping("/mis-tickets")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mis tickets", description = "Devuelve los tickets del trabajador autenticado.")
    public ResponseEntity<List<TicketDTO>> misTickets(Authentication authentication) {
        return ResponseEntity.ok(ticketService.findMisTickets(authentication.getName()));
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Obtener ticket", description = "Obtiene un ticket por su ID")
    public ResponseEntity<TicketDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ticketService.findById(id));
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar ticket", description = "Borrado lógico: deja de aparecer en los listados pero se conserva para auditoría")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        ticketService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar tickets en masa", description = "Borrado lógico de varios tickets a la vez; conserva los registros para auditoría")
    public ResponseEntity<Void> eliminarMasivo(@RequestBody List<Long> ids) {
        ticketService.eliminarMasivo(ids);
        return ResponseEntity.noContent().build();
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

    @PostMapping("/ocr-preview")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Preview OCR de ticket (multi-foto)",
            description = "Valida la asignación activa de la tarjeta y, si OK, extrae las líneas del ticket a partir de N fotos vía IA SIN crear ticket. FLEET-01: el PIN ya no se exige aquí; existe solo a efectos de consulta.")
    public ResponseEntity<Map<String, Object>> ocrPreview(
            @RequestParam("imagenes") MultipartFile[] imagenes,
            @RequestParam("tarjetaId") Long tarjetaId,
            Authentication authentication) {
        try {
            if (imagenes == null || imagenes.length == 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Al menos una imagen es obligatoria"));
            }

            // ── Validar asignación ANTES de hacer OCR ────────────────────
            try {
                ticketService.validarAsignacion(authentication.getName(), tarjetaId);
            } catch (com.tecozam.bills.shared.infrastructure.exception.BusinessException be) {
                return ResponseEntity.badRequest().body(Map.of("error", be.getMessage()));
            }

            List<LineaOcrDTO> lineas;
            try {
                lineas = parsearLineas(imagenes);
            } catch (IllegalStateException ise) {
                return ResponseEntity.internalServerError().body(Map.of("error", ise.getMessage()));
            }

            return ResponseEntity.ok(Map.of("lineas", lineas));
        } catch (Exception e) {
            log.error("[OCR-PREVIEW] Exception: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Error al procesar preview OCR: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/ocr-validado")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "OCR validado con PIN (multi-foto)",
            description = "Crea un ticket con OCR_VALIDADO a partir de N fotos. Si en params vienen las líneas ya editadas (preview previo) las usa directamente sin volver a llamar a la IA. Si no, hace OCR de fallback con las imágenes.")
    public ResponseEntity<Map<String, Object>> ocrValidado(
            @RequestParam("imagenes") MultipartFile[] imagenes,
            @RequestPart("params") @Valid CreateTicketOcrValidadoRequest params,
            Authentication authentication) {
        try {
            if (imagenes == null || imagenes.length == 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Al menos una imagen es obligatoria"));
            }

            List<LineaOcrDTO> lineas = params.lineas();
            if (lineas == null || lineas.isEmpty()) {
                // Modo legacy: cliente no envió preview editado, hacer OCR ahora
                log.info("[OCR-VALIDADO] Cliente no envió líneas, haciendo OCR fallback");
                try {
                    lineas = parsearLineas(imagenes);
                } catch (IllegalStateException ise) {
                    return ResponseEntity.internalServerError().body(Map.of("error", ise.getMessage()));
                }
            } else {
                log.info("[OCR-VALIDADO] Usando líneas del cliente (preview previo), {} líneas", lineas.size());
            }

            try {
                TicketDTO ticket = ticketService.createOcrValidado(
                        authentication.getName(), params, lineas, Arrays.asList(imagenes));

                Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("status", "ok");
                response.put("ticketId", ticket.id());
                response.put("message", "Ticket creado: " + ticket.estacion() + " — " + ticket.importeTotal() + "€");
                return ResponseEntity.ok(response);
            } catch (com.tecozam.bills.shared.infrastructure.exception.BusinessException be) {
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
     * Llama a {@link LlmTicketParser} con las N fotos y mapea el resultado al
     * DTO que viaja por la API. Lanza {@link IllegalStateException} si falta
     * la API key (el propio parser la valida).
     */
    private List<LineaOcrDTO> parsearLineas(MultipartFile[] imagenes) throws Exception {
        List<ImagenTicket> fotos = new java.util.ArrayList<>();
        for (MultipartFile imagen : imagenes) {
            String mimeType = imagen.getContentType() != null ? imagen.getContentType() : "image/jpeg";
            fotos.add(new ImagenTicket(imagen.getBytes(), mimeType));
        }
        List<LineaTicketExtraida> extraidas = llmTicketParser.parse(fotos);
        return extraidas.stream().map(this::toLineaOcrDTO).toList();
    }

    private LineaOcrDTO toLineaOcrDTO(LineaTicketExtraida l) {
        return new LineaOcrDTO(
                l.fotoIndice(),
                l.estacion(),
                l.fechaHora(),
                l.producto(),
                l.numRecibo(),
                l.matricula(),
                l.kms(),
                l.litros(),
                l.precioLitro(),
                l.importeTotal()
        );
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TicketController.class);
}
