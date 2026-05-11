package com.tecozam.bills.asistente.tools.tools;

import com.tecozam.bills.asistente.tools.ChartSpec;
import com.tecozam.bills.asistente.tools.Tool;
import com.tecozam.bills.asistente.tools.ToolResult;
import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class OperacionesRecientesTool implements Tool {

    private final OperacionRepository operacionRepository;

    @Override
    public String getName() {
        return "get_operaciones_recientes";
    }

    @Override
    public String getDescription() {
        return "Devuelve las últimas N operaciones de gasto ordenadas por fecha descendente.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "limit", Map.of("type", "integer", "description", "Número de operaciones a devolver (máx 50)", "default", 10)
            ),
            "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        int limit = args.containsKey("limit") ? Math.min(((Number) args.get("limit")).intValue(), 50) : 10;

        List<Map<String, Object>> data = operacionRepository.findAll().stream()
            .filter(o -> o.getFechaHora() != null)
            .sorted((a, b) -> b.getFechaHora().compareTo(a.getFechaHora()))
            .limit(limit)
            .map(o -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", o.getId());
                row.put("fecha", o.getFechaHora() != null ? o.getFechaHora().toString() : null);
                row.put("conductor", o.getTarjetaResumen() != null ? o.getTarjetaResumen().getConductor() : null);
                row.put("estacion", o.getEstablecimiento());
                row.put("importe", o.getImporteTotal() != null ? o.getImporteTotal().doubleValue() : 0.0);
                row.put("concepto", o.getConceptoUnificado());
                row.put("litros", o.getCantidad() != null ? o.getCantidad().doubleValue() : null);
                return row;
            })
            .collect(Collectors.toList());

        log.info("[Asistente] tool={} args={} resultRows={}", getName(), args, data.size());

        ChartSpec chart = new ChartSpec(
            "table",
            "Operaciones recientes",
            "Últimas " + limit + " operaciones",
            data,
            Map.of("columns", List.of("fecha", "conductor", "estacion", "importe", "concepto"))
        );

        return new ToolResult(getName(), args, data, chart);
    }
}
