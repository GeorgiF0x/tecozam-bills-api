package com.tecozam.bills.asistente.tools.tools;

import com.tecozam.bills.alerta.infrastructure.persistence.AlertaPrestamoRepository;
import com.tecozam.bills.asistente.tools.ChartSpec;
import com.tecozam.bills.asistente.tools.Tool;
import com.tecozam.bills.asistente.tools.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertasPendientesTool implements Tool {

    private final AlertaPrestamoRepository alertaRepository;

    @Override
    public String getName() {
        return "get_alertas_pendientes";
    }

    @Override
    public String getDescription() {
        return "Devuelve las alertas no leídas. Filtro opcional por tipo de alerta.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "tipo", Map.of("type", "string", "description", "Filtrar por tipo de alerta (opcional)")
            ),
            "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String tipoFiltro = args.containsKey("tipo") ? (String) args.get("tipo") : null;

        List<Map<String, Object>> data = alertaRepository.findByLeidaFalseOrderByFechaAlertaDesc().stream()
            .filter(a -> tipoFiltro == null || tipoFiltro.equalsIgnoreCase(a.getTipoAlerta()))
            .map(a -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", a.getId());
                row.put("tipo", a.getTipoAlerta());
                row.put("mensaje", a.getMensaje());
                row.put("fecha", a.getFechaAlerta() != null ? a.getFechaAlerta().toString() : null);
                row.put("urgencia", determinarUrgencia(a.getTipoAlerta()));
                return row;
            })
            .collect(Collectors.toList());

        log.info("[Asistente] tool={} args={} resultRows={}", getName(), args, data.size());

        ChartSpec chart = new ChartSpec(
            "table",
            "Alertas pendientes",
            data.size() + " alerta(s) sin leer",
            data,
            Map.of("columns", List.of("tipo", "mensaje", "fecha", "urgencia"))
        );

        return new ToolResult(getName(), args, data, chart);
    }

    private String determinarUrgencia(String tipo) {
        if (tipo == null) return "NORMAL";
        return switch (tipo.toUpperCase()) {
            case "VENCIMIENTO_INMEDIATO", "PRESTAMO_VENCIDO" -> "ALTA";
            case "PROXIMO_VENCIMIENTO" -> "MEDIA";
            default -> "NORMAL";
        };
    }
}
