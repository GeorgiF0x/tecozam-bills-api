package com.tecozam.bills.asistente.application;

import com.tecozam.bills.asistente.dto.AsistenteResponse;
import com.tecozam.bills.factura.infrastructure.persistence.FacturaRepository;
import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import com.tecozam.bills.alerta.infrastructure.persistence.AlertaPrestamoRepository;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import com.tecozam.bills.vehiculo.infrastructure.persistence.VehiculoRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.centrocoste.infrastructure.persistence.CentroCosteRepository;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class AsistenteService {

    private final FacturaRepository facturaRepository;
    private final OperacionRepository operacionRepository;
    private final PrestamoRepository prestamoRepository;
    private final TicketRepository ticketRepository;
    private final AlertaPrestamoRepository alertaRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final VehiculoRepository vehiculoRepository;
    private final TarjetaRepository tarjetaRepository;
    private final ProveedorRepository proveedorRepository;
    private final CentroCosteRepository centroCosteRepository;
    private final ViatRepository viatRepository;
    private final com.tecozam.bills.tarifa.infrastructure.persistence.TarifaRepository tarifaRepository;

    @Value("${app.openai.api-key:}")
    private String apiKey;

    @Value("${app.openai.model:gpt-4.1-mini}")
    private String model;

    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    public AsistenteResponse consultar(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            return new AsistenteResponse(
                "El asistente no está configurado. Falta la clave OPENAI_API_KEY.",
                "error"
            );
        }

        try {
            String context = buildContext();
            String systemPrompt = buildSystemPrompt(context);
            String responseText = callOpenAI(systemPrompt, query);
            return new AsistenteResponse(responseText, "text");
        } catch (Exception e) {
            log.error("[Asistente] Error al consultar: {}", e.getMessage(), e);
            return new AsistenteResponse(
                "Error al procesar la consulta: " + e.getMessage(),
                "error"
            );
        }
    }

    private String buildContext() {
        var Collectors = java.util.stream.Collectors.class;
        StringBuilder sb = new StringBuilder();
        sb.append("=== TECOZAM BILLS — CONTEXTO COMPLETO ===\n\n");

        // ── Resumen general ────────────────────────────────────────────
        sb.append(String.format("[Resumen] Facturas: %d | Operaciones: %d | Tickets: %d | Préstamos: %d | Alertas: %d | Trabajadores: %d | Vehículos: %d | Tarjetas: %d\n\n",
                facturaRepository.count(), operacionRepository.count(), ticketRepository.count(),
                prestamoRepository.count(), alertaRepository.count(),
                trabajadorRepository.count(), vehiculoRepository.count(), tarjetaRepository.count()));

        // ── Proveedores ────────────────────────────────────────────────
        try {
            var proveedores = proveedorRepository.findAll();
            sb.append("[Proveedores]\n");
            for (var p : proveedores) {
                sb.append(String.format("  %s (código:%s, NIF:%s, activo:%s)\n",
                        p.getNombre(), p.getCodigo(), p.getNif() != null ? p.getNif() : "—", p.isActivo()));
            }
            sb.append("\n");
        } catch (Exception e) { log.warn("[Asistente] {}", e.getMessage()); }

        // ── Trabajadores ───────────────────────────────────────────────
        try {
            var trabajadores = trabajadorRepository.findAll();
            sb.append("[Trabajadores]\n");
            for (var t : trabajadores) {
                sb.append(String.format("  %s %s | email:%s | activo:%s\n",
                        t.getNombre(), t.getApellidos(), t.getEmail() != null ? t.getEmail() : "—", t.isActivo()));
            }
            sb.append("\n");
        } catch (Exception e) { log.warn("[Asistente] {}", e.getMessage()); }

        // ── Vehículos ──────────────────────────────────────────────────
        try {
            var vehiculos = vehiculoRepository.findAll();
            if (!vehiculos.isEmpty()) {
                sb.append("[Vehículos]\n");
                for (var v : vehiculos) {
                    sb.append(String.format("  %s | tipo:%s | estado:%s | activo:%s\n",
                            v.getMatricula(), v.getTipo(), v.getEstado(), v.isActivo()));
                }
                sb.append("\n");
            }
        } catch (Exception e) { log.warn("[Asistente] {}", e.getMessage()); }

        // ── Centros de Coste ───────────────────────────────────────────
        try {
            var centros = centroCosteRepository.findAll();
            if (!centros.isEmpty()) {
                sb.append("[Centros de Coste]\n");
                for (var c : centros) sb.append(String.format("  %s: %s (activo:%s)\n", c.getCodigo(), c.getNombre(), c.isActivo()));
                sb.append("\n");
            }
        } catch (Exception e) { log.warn("[Asistente] {}", e.getMessage()); }

        // ── VIATs ──────────────────────────────────────────────────────
        try {
            var viats = viatRepository.findAll();
            if (!viats.isEmpty()) {
                sb.append("[VIATs]\n");
                for (var v : viats) sb.append(String.format("  %s | serie:%s | estado:%s\n", v.getCodigo(), v.getNumeroSerie() != null ? v.getNumeroSerie() : "—", v.getEstado()));
                sb.append("\n");
            }
        } catch (Exception e) { log.warn("[Asistente] {}", e.getMessage()); }

        // ── Tarifas de proveedores ─────────────────────────────────────
        try {
            var tarifas = tarifaRepository.findAll();
            if (!tarifas.isEmpty()) {
                sb.append("[Tarifas de proveedores]\n");
                for (var t : tarifas) {
                    sb.append(String.format("  Tarifa: %s | Proveedor: %s | Vigente: %s → %s\n",
                            t.getCodigoTarifa() != null ? t.getCodigoTarifa() : "—",
                            t.getProveedor() != null ? t.getProveedor().getNombre() : "?",
                            t.getVigenteDesde(),
                            t.getVigenteHasta() != null ? t.getVigenteHasta().toString() : "activa"));
                    for (var p : t.getPrecios()) {
                        sb.append(String.format("    %s (%s): sin IVA %.3f€/L, con IVA %.3f€/L\n",
                                p.getProducto(),
                                p.getConceptoUnificado() != null ? p.getConceptoUnificado() : "—",
                                p.getPrecioSinIva(), p.getPrecioConIva()));
                    }
                }
                sb.append("\n");
            }
        } catch (Exception e) { log.warn("[Asistente] {}", e.getMessage()); }

        // ── Facturas detalle ───────────────────────────────────────────
        try {
            var facturas = facturaRepository.findAll();
            sb.append("[Facturas]\n");
            for (var f : facturas) {
                int nTarj = f.getTarjetaResumenes() != null ? f.getTarjetaResumenes().size() : 0;
                int nOps = f.getTarjetaResumenes() != null ? f.getTarjetaResumenes().stream().mapToInt(tr -> tr.getOperaciones().size()).sum() : 0;
                sb.append(String.format("  %s | proveedor:%s | fecha:%s | base:%.2f€ | iva:%.2f€ | total:%.2f€ | tarjetas:%d | ops:%d\n",
                        f.getNumFactura(),
                        f.getProveedor() != null ? f.getProveedor().getNombre() : "?",
                        f.getFecha(),
                        f.getBaseImponible() != null ? f.getBaseImponible().doubleValue() : 0,
                        f.getTotalIva() != null ? f.getTotalIva().doubleValue() : 0,
                        f.getTotalFactura() != null ? f.getTotalFactura().doubleValue() : 0,
                        nTarj, nOps));
            }
            sb.append("\n");
        } catch (Exception e) { log.warn("[Asistente] {}", e.getMessage()); }

        // ── Operaciones: análisis cruzado ──────────────────────────────
        try {
            var ops = operacionRepository.findAll();

            // Por fecha
            sb.append("[Operaciones por fecha]\n");
            var byDate = ops.stream().filter(o -> o.getFechaHora() != null)
                    .collect(java.util.stream.Collectors.groupingBy(o -> o.getFechaHora().toLocalDate(), java.util.TreeMap::new, java.util.stream.Collectors.toList()));
            for (var e : byDate.entrySet()) {
                double t = e.getValue().stream().filter(o -> o.getImporteTotal() != null).mapToDouble(o -> o.getImporteTotal().doubleValue()).sum();
                double l = e.getValue().stream().filter(o -> o.getCantidad() != null).mapToDouble(o -> o.getCantidad().doubleValue()).sum();
                sb.append(String.format("  %s: %d ops, %.2f€, %.1fL\n", e.getKey(), e.getValue().size(), t, l));
            }
            sb.append("\n");

            // Por concepto
            sb.append("[Operaciones por concepto]\n");
            ops.stream().collect(java.util.stream.Collectors.groupingBy(o -> o.getConceptoUnificado() != null ? o.getConceptoUnificado() : "OTRO"))
                    .forEach((k, v) -> {
                        double t = v.stream().filter(o -> o.getImporteTotal() != null).mapToDouble(o -> o.getImporteTotal().doubleValue()).sum();
                        double l = v.stream().filter(o -> o.getCantidad() != null).mapToDouble(o -> o.getCantidad().doubleValue()).sum();
                        sb.append(String.format("  %s: %d ops, %.2f€, %.1fL\n", k, v.size(), t, l));
                    });
            sb.append("\n");

            // CRUCE: Conductor × Proveedor (clave para preguntas como "quién reposta con MOEVE")
            sb.append("[Conductor × Proveedor]\n");
            ops.stream().filter(o -> o.getTarjetaResumen() != null && o.getTarjetaResumen().getConductor() != null)
                    .collect(java.util.stream.Collectors.groupingBy(o -> {
                        String cond = o.getTarjetaResumen().getConductor();
                        String prov = "?";
                        if (o.getTarjetaResumen().getFactura() != null && o.getTarjetaResumen().getFactura().getProveedor() != null)
                            prov = o.getTarjetaResumen().getFactura().getProveedor().getNombre();
                        return cond + " | " + prov;
                    })).entrySet().stream()
                    .sorted((a, b) -> Double.compare(
                            b.getValue().stream().filter(o -> o.getImporteTotal() != null).mapToDouble(o -> o.getImporteTotal().doubleValue()).sum(),
                            a.getValue().stream().filter(o -> o.getImporteTotal() != null).mapToDouble(o -> o.getImporteTotal().doubleValue()).sum()))
                    .limit(40)
                    .forEach(e -> {
                        double t = e.getValue().stream().filter(o -> o.getImporteTotal() != null).mapToDouble(o -> o.getImporteTotal().doubleValue()).sum();
                        double l = e.getValue().stream().filter(o -> o.getCantidad() != null).mapToDouble(o -> o.getCantidad().doubleValue()).sum();
                        sb.append(String.format("  %s: %d ops, %.2f€, %.1fL\n", e.getKey(), e.getValue().size(), t, l));
                    });
            sb.append("\n");

            // CRUCE: Conductor × Concepto
            sb.append("[Conductor × Concepto (top 30)]\n");
            ops.stream().filter(o -> o.getTarjetaResumen() != null && o.getTarjetaResumen().getConductor() != null)
                    .collect(java.util.stream.Collectors.groupingBy(o ->
                            o.getTarjetaResumen().getConductor() + " | " + (o.getConceptoUnificado() != null ? o.getConceptoUnificado() : "OTRO")))
                    .entrySet().stream()
                    .sorted((a, b) -> Double.compare(
                            b.getValue().stream().filter(o -> o.getImporteTotal() != null).mapToDouble(o -> o.getImporteTotal().doubleValue()).sum(),
                            a.getValue().stream().filter(o -> o.getImporteTotal() != null).mapToDouble(o -> o.getImporteTotal().doubleValue()).sum()))
                    .limit(30)
                    .forEach(e -> {
                        double t = e.getValue().stream().filter(o -> o.getImporteTotal() != null).mapToDouble(o -> o.getImporteTotal().doubleValue()).sum();
                        sb.append(String.format("  %s: %d ops, %.2f€\n", e.getKey(), e.getValue().size(), t));
                    });
            sb.append("\n");

            // Top establecimientos
            sb.append("[Top 15 establecimientos por gasto]\n");
            ops.stream().filter(o -> o.getEstablecimiento() != null && !o.getEstablecimiento().isBlank())
                    .collect(java.util.stream.Collectors.groupingBy(o -> o.getEstablecimiento().length() > 40 ? o.getEstablecimiento().substring(0, 40) : o.getEstablecimiento()))
                    .entrySet().stream()
                    .sorted((a, b) -> Double.compare(
                            b.getValue().stream().filter(o -> o.getImporteTotal() != null).mapToDouble(o -> o.getImporteTotal().doubleValue()).sum(),
                            a.getValue().stream().filter(o -> o.getImporteTotal() != null).mapToDouble(o -> o.getImporteTotal().doubleValue()).sum()))
                    .limit(15)
                    .forEach(e -> {
                        double t = e.getValue().stream().filter(o -> o.getImporteTotal() != null).mapToDouble(o -> o.getImporteTotal().doubleValue()).sum();
                        sb.append(String.format("  %s: %d ops, %.2f€\n", e.getKey(), e.getValue().size(), t));
                    });
            sb.append("\n");

            // Anomalías: operaciones fuera de horario
            sb.append("[Anomalías: operaciones fuera de horario laboral (antes 7:00, después 20:00, fines de semana)]\n");
            long anomalias = ops.stream().filter(o -> o.getFechaHora() != null).filter(o -> {
                int h = o.getFechaHora().getHour();
                var d = o.getFechaHora().getDayOfWeek();
                return h < 7 || h >= 20 || d == java.time.DayOfWeek.SATURDAY || d == java.time.DayOfWeek.SUNDAY;
            }).count();
            sb.append(String.format("  Total operaciones fuera de horario: %d de %d (%.1f%%)\n\n", anomalias, ops.size(), ops.size() > 0 ? anomalias * 100.0 / ops.size() : 0));

            // Estadísticas globales
            double totalImporte = ops.stream().filter(o -> o.getImporteTotal() != null).mapToDouble(o -> o.getImporteTotal().doubleValue()).sum();
            double totalLitros = ops.stream().filter(o -> o.getCantidad() != null).mapToDouble(o -> o.getCantidad().doubleValue()).sum();
            double mediaOp = ops.size() > 0 ? totalImporte / ops.size() : 0;
            sb.append(String.format("[Totales] Importe: %.2f€ | Litros: %.1fL | Media por operación: %.2f€\n\n", totalImporte, totalLitros, mediaOp));

        } catch (Exception e) {
            log.warn("[Asistente] Error operaciones: {}", e.getMessage());
        }

        // ── Préstamos ──────────────────────────────────────────────────
        try {
            var prestamos = prestamoRepository.findAll();
            if (!prestamos.isEmpty()) {
                sb.append("[Préstamos]\n");
                long act = prestamos.stream().filter(p -> "ACTIVO".equals(p.getEstado().toString())).count();
                long ven = prestamos.stream().filter(p -> "VENCIDO".equals(p.getEstado().toString())).count();
                long dev = prestamos.stream().filter(p -> "DEVUELTO".equals(p.getEstado().toString())).count();
                sb.append(String.format("  Activos: %d | Vencidos: %d | Devueltos: %d\n", act, ven, dev));
                for (var p : prestamos) {
                    sb.append(String.format("  - Tipo:%s | Trabajador:%s | Estado:%s | Inicio:%s | FinPrevista:%s\n",
                            p.getTipoRecurso() != null ? p.getTipoRecurso() : "?",
                            p.getTrabajador() != null ? p.getTrabajador().getNombre() + " " + p.getTrabajador().getApellidos() : "?",
                            p.getEstado(), p.getFechaInicio(), p.getFechaFinPrevista()));
                }
                sb.append("\n");
            }
        } catch (Exception e) { log.warn("[Asistente] {}", e.getMessage()); }

        // ── Tickets ────────────────────────────────────────────────────
        try {
            var tickets = ticketRepository.findAll();
            if (!tickets.isEmpty()) {
                sb.append("[Tickets]\n");
                long pen = tickets.stream().filter(t -> "PENDIENTE".equals(t.getEstadoCotejo().toString())).count();
                long cot = tickets.stream().filter(t -> "COTEJADO".equals(t.getEstadoCotejo().toString())).count();
                long inc = tickets.stream().filter(t -> "INCIDENCIA".equals(t.getEstadoCotejo().toString())).count();
                sb.append(String.format("  Pendientes: %d | Cotejados: %d | Incidencias: %d\n\n", pen, cot, inc));
            }
        } catch (Exception e) { log.warn("[Asistente] {}", e.getMessage()); }

        // ── Alertas ────────────────────────────────────────────────────
        try {
            var alertas = alertaRepository.findAll();
            if (!alertas.isEmpty()) {
                sb.append("[Alertas]\n");
                long noLeidas = alertas.stream().filter(a -> !a.isLeida()).count();
                long leidas = alertas.stream().filter(a -> a.isLeida()).count();
                sb.append(String.format("  Total: %d | No leídas: %d | Leídas: %d\n", alertas.size(), noLeidas, leidas));

                // Count by type
                var byTipo = alertas.stream().filter(a -> !a.isLeida())
                        .collect(java.util.stream.Collectors.groupingBy(a -> a.getTipoAlerta().toString(), java.util.stream.Collectors.counting()));
                for (var entry : byTipo.entrySet()) {
                    sb.append(String.format("  %s (no leídas): %d\n", entry.getKey(), entry.getValue()));
                }

                // List non-read alerts with messages
                sb.append("  Alertas pendientes:\n");
                alertas.stream().filter(a -> !a.isLeida())
                        .forEach(a -> sb.append(String.format("    - [%s] %s (fecha: %s)\n",
                                a.getTipoAlerta(), a.getMensaje() != null ? a.getMensaje() : "—", a.getFechaAlerta())));
                sb.append("\n");
            }
        } catch (Exception e) { log.warn("[Asistente] {}", e.getMessage()); }

        return sb.toString();
    }

    private String buildSystemPrompt(String context) {
        return "Eres el asistente IA de Tecozam Bills Manager, un sistema de control de gastos de flota.\n" +
            "Tu rol es responder consultas sobre el estado del sistema de forma clara y concisa.\n\n" +
            "Reglas:\n" +
            "- Responde SIEMPRE en español\n" +
            "- Sé directo y útil, respuestas cortas\n" +
            "- Usa formato Markdown para estructurar (tablas, listas, negritas)\n" +
            "- Si no tienes datos en el contexto, dilo claramente\n" +
            "- NO inventes datos — solo usa el contexto proporcionado\n" +
            "- Para importes usa formato EUR (ej: 1.234,56 €)\n\n" +
            "CONTEXTO ACTUAL DEL SISTEMA:\n" + context;
    }

    private String callOpenAI(String systemPrompt, String userQuery) throws Exception {
        String escapedSystem = escapeJson(systemPrompt);
        String escapedUser = escapeJson(userQuery);

        String requestBody = "{" +
            "\"model\":\"" + model + "\"," +
            "\"messages\":[" +
                "{\"role\":\"system\",\"content\":\"" + escapedSystem + "\"}," +
                "{\"role\":\"user\",\"content\":\"" + escapedUser + "\"}" +
            "]," +
            "\"max_tokens\":1500," +
            "\"temperature\":0.2" +
            "}";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("[Asistente] OpenAI returned {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Error del servicio de IA (HTTP " + response.statusCode() + ")");
        }

        // Extract choices[0].message.content from JSON response
        String body = response.body();
        int contentIdx = body.indexOf("\"content\":");
        if (contentIdx == -1) {
            throw new RuntimeException("Respuesta inesperada del servicio de IA");
        }
        // Find the opening quote of the content value
        int valueStart = body.indexOf("\"", contentIdx + 10) + 1;
        int valueEnd = findClosingQuote(body, valueStart);
        String content = body.substring(valueStart, valueEnd);
        // Unescape
        content = content.replace("\\n", "\n")
                         .replace("\\\"", "\"")
                         .replace("\\\\", "\\")
                         .replace("\\t", "\t");
        return content;
    }

    private int findClosingQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            if (s.charAt(i) == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                return i;
            }
        }
        return s.length();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
