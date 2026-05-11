package com.tecozam.bills.asistente.tools.tools;

import com.tecozam.bills.asistente.tools.ChartSpec;
import com.tecozam.bills.asistente.tools.Tool;
import com.tecozam.bills.asistente.tools.ToolResult;
import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class GastoPorConceptoTool implements Tool {

    private final OperacionRepository operacionRepository;

    @Override
    public String getName() {
        return "get_gasto_por_concepto";
    }

    @Override
    public String getDescription() {
        return "Devuelve el gasto agrupado por concepto unificado (DIESEL, PEAJE, LAVADO, etc.) en un rango de fechas.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "from", Map.of("type", "string", "format", "date", "description", "Fecha desde YYYY-MM-DD"),
                "to", Map.of("type", "string", "format", "date", "description", "Fecha hasta YYYY-MM-DD")
            ),
            "required", List.of("from", "to")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        LocalDate from = LocalDate.parse((String) args.get("from"));
        LocalDate to = LocalDate.parse((String) args.get("to"));

        LocalDateTime dtFrom = from.atStartOfDay();
        LocalDateTime dtTo = to.atTime(LocalTime.MAX);

        List<Map<String, Object>> data = operacionRepository.findAll().stream()
            .filter(o -> o.getFechaHora() != null
                && !o.getFechaHora().isBefore(dtFrom)
                && !o.getFechaHora().isAfter(dtTo))
            .collect(Collectors.groupingBy(
                o -> o.getConceptoUnificado() != null ? o.getConceptoUnificado() : "OTRO"
            ))
            .entrySet().stream()
            .map(e -> {
                double importe = e.getValue().stream()
                    .filter(o -> o.getImporteTotal() != null)
                    .mapToDouble(o -> o.getImporteTotal().doubleValue())
                    .sum();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("concepto", e.getKey());
                row.put("importe", Math.round(importe * 100.0) / 100.0);
                row.put("numOperaciones", e.getValue().size());
                return row;
            })
            .sorted((a, b) -> Double.compare((double) b.get("importe"), (double) a.get("importe")))
            .collect(Collectors.toList());

        log.info("[Asistente] tool={} args={} resultRows={}", getName(), args, data.size());

        ChartSpec chart = new ChartSpec(
            "pie",
            "Gasto por concepto",
            "Del " + from + " al " + to,
            data,
            Map.of("nameKey", "concepto", "valueKey", "importe")
        );

        return new ToolResult(getName(), args, data, chart);
    }
}
