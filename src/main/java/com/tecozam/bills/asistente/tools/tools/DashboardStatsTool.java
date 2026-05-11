package com.tecozam.bills.asistente.tools.tools;

import com.tecozam.bills.alerta.infrastructure.persistence.AlertaPrestamoRepository;
import com.tecozam.bills.asistente.tools.ChartSpec;
import com.tecozam.bills.asistente.tools.Tool;
import com.tecozam.bills.asistente.tools.ToolResult;
import com.tecozam.bills.factura.infrastructure.persistence.FacturaRepository;
import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardStatsTool implements Tool {

    private final FacturaRepository facturaRepository;
    private final OperacionRepository operacionRepository;
    private final TicketRepository ticketRepository;
    private final AlertaPrestamoRepository alertaRepository;
    private final PrestamoRepository prestamoRepository;

    @Override
    public String getName() {
        return "get_dashboard_stats";
    }

    @Override
    public String getDescription() {
        return "Devuelve las métricas generales del dashboard: total facturas, importe total, operaciones, alertas pendientes, anomalías, préstamos activos.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(),
            "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        long totalFacturas = facturaRepository.count();
        long totalOperaciones = operacionRepository.count();
        double importeTotal = Optional.ofNullable(facturaRepository.sumTotalFactura())
            .orElse(BigDecimal.ZERO).doubleValue();
        long alertasPendientes = alertaRepository.countByLeidaFalse();
        long anomalias = ticketRepository.countByEstadoCotejoIn(List.of("INCIDENCIA", "SIN_COINCIDENCIA"));
        long prestamosActivos = prestamoRepository.countByEstado("ACTIVO");
        long totalTickets = ticketRepository.count();
        long ticketsCotejados = ticketRepository.findByEstadoCotejo("COTEJADO").size();
        double pctCotejado = totalTickets > 0 ? (ticketsCotejados * 100.0) / totalTickets : 0;

        List<Map<String, Object>> data = List.of(
            kpi("Total facturas", totalFacturas, null),
            kpi("Total operaciones", totalOperaciones, null),
            kpi("Importe total (€)", importeTotal, null),
            kpi("Alertas pendientes", alertasPendientes, null),
            kpi("Anomalías / incidencias", anomalias, null),
            kpi("Préstamos activos", prestamosActivos, null),
            kpi("% tickets cotejados", Math.round(pctCotejado * 10.0) / 10.0, null)
        );

        log.info("[Asistente] tool={} args={} resultRows={}", getName(), args, data.size());

        ChartSpec chart = new ChartSpec(
            "kpi",
            "Resumen del dashboard",
            null,
            data,
            Map.of("labelKey", "label", "valueKey", "value")
        );

        return new ToolResult(getName(), args, data, chart);
    }

    private Map<String, Object> kpi(String label, Object value, Object delta) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        if (delta != null) m.put("delta", delta);
        return m;
    }
}
