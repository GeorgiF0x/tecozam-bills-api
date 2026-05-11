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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class EvolucionGastoTool implements Tool {

    private final OperacionRepository operacionRepository;

    @Override
    public String getName() {
        return "get_evolucion_gasto";
    }

    @Override
    public String getDescription() {
        return "Devuelve la evolución temporal del gasto con granularidad configurable (day, week, month). Ideal para gráficos de tendencia.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "from", Map.of("type", "string", "format", "date", "description", "Fecha desde YYYY-MM-DD"),
                "to", Map.of("type", "string", "format", "date", "description", "Fecha hasta YYYY-MM-DD"),
                "granularity", Map.of("type", "string", "enum", List.of("day", "week", "month"), "description", "Granularidad: day, week o month", "default", "month")
            ),
            "required", List.of("from", "to")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        LocalDate from = LocalDate.parse((String) args.get("from"));
        LocalDate to = LocalDate.parse((String) args.get("to"));
        String granularity = args.containsKey("granularity") ? (String) args.get("granularity") : "month";

        LocalDateTime dtFrom = from.atStartOfDay();
        LocalDateTime dtTo = to.atTime(LocalTime.MAX);

        List<Map<String, Object>> data = operacionRepository.findAll().stream()
            .filter(o -> o.getFechaHora() != null
                && !o.getFechaHora().isBefore(dtFrom)
                && !o.getFechaHora().isAfter(dtTo))
            .collect(Collectors.groupingBy(o -> groupKey(o.getFechaHora(), granularity)))
            .entrySet().stream()
            .map(e -> {
                double importe = e.getValue().stream()
                    .filter(o -> o.getImporteTotal() != null)
                    .mapToDouble(o -> o.getImporteTotal().doubleValue())
                    .sum();
                double litros = e.getValue().stream()
                    .filter(o -> o.getCantidad() != null)
                    .mapToDouble(o -> o.getCantidad().doubleValue())
                    .sum();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("fecha", e.getKey());
                row.put("importe", Math.round(importe * 100.0) / 100.0);
                row.put("litros", Math.round(litros * 10.0) / 10.0);
                row.put("numOperaciones", e.getValue().size());
                return row;
            })
            .sorted(Comparator.comparing(m -> (String) m.get("fecha")))
            .collect(Collectors.toList());

        log.info("[Asistente] tool={} args={} resultRows={}", getName(), args, data.size());

        ChartSpec chart = new ChartSpec(
            "area",
            "Evolución del gasto",
            "Del " + from + " al " + to + " (" + granularity + ")",
            data,
            Map.of("xKey", "fecha", "yKey", "importe", "secondaryKey", "litros")
        );

        return new ToolResult(getName(), args, data, chart);
    }

    private String groupKey(LocalDateTime dt, String granularity) {
        return switch (granularity) {
            case "day" -> dt.format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "week" -> dt.getYear() + "-W" + String.format("%02d", dt.getDayOfYear() / 7 + 1);
            default -> dt.getYear() + "-" + String.format("%02d", dt.getMonthValue());
        };
    }
}
