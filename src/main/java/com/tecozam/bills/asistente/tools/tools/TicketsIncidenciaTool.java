package com.tecozam.bills.asistente.tools.tools;

import com.tecozam.bills.asistente.tools.ChartSpec;
import com.tecozam.bills.asistente.tools.Tool;
import com.tecozam.bills.asistente.tools.ToolResult;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketsIncidenciaTool implements Tool {

    private final TicketRepository ticketRepository;

    @Override
    public String getName() {
        return "get_tickets_con_incidencia";
    }

    @Override
    public String getDescription() {
        return "Devuelve los tickets con estado INCIDENCIA o SIN_COINCIDENCIA que requieren atención.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "limit", Map.of("type", "integer", "description", "Número máximo de tickets a devolver", "default", 20)
            ),
            "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 20;

        List<Map<String, Object>> data = ticketRepository
            .findByEstadoCotejoIn(List.of("INCIDENCIA", "SIN_COINCIDENCIA")).stream()
            .sorted((a, b) -> {
                if (a.getFechaHora() == null && b.getFechaHora() == null) return 0;
                if (a.getFechaHora() == null) return 1;
                if (b.getFechaHora() == null) return -1;
                return b.getFechaHora().compareTo(a.getFechaHora());
            })
            .limit(limit)
            .map(t -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", t.getId());
                row.put("estacion", t.getEstacion());
                row.put("importe", t.getImporteTotal() != null ? t.getImporteTotal().doubleValue() : 0.0);
                row.put("tipoIncidencia", t.getTipoIncidencia());
                row.put("estadoCotejo", t.getEstadoCotejo());
                row.put("fecha", t.getFechaHora() != null ? t.getFechaHora().toString() : null);
                row.put("trabajador", t.getTrabajador() != null
                    ? t.getTrabajador().getNombre() + " " + t.getTrabajador().getApellidos() : null);
                row.put("proveedor", t.getProveedor() != null ? t.getProveedor().getNombre() : null);
                return row;
            })
            .collect(Collectors.toList());

        log.info("[Asistente] tool={} args={} resultRows={}", getName(), args, data.size());

        ChartSpec chart = new ChartSpec(
            "table",
            "Tickets con incidencia",
            data.size() + " ticket(s) pendientes de resolución",
            data,
            Map.of("columns", List.of("id", "estacion", "importe", "tipoIncidencia", "estadoCotejo", "fecha"))
        );

        return new ToolResult(getName(), args, data, chart);
    }
}
