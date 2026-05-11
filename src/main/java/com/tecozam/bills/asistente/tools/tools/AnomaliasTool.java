package com.tecozam.bills.asistente.tools.tools;

import com.tecozam.bills.asistente.tools.ChartSpec;
import com.tecozam.bills.asistente.tools.Tool;
import com.tecozam.bills.asistente.tools.ToolResult;
import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnomaliasTool implements Tool {

    private final OperacionRepository operacionRepository;

    @Override
    public String getName() {
        return "get_anomalias";
    }

    @Override
    public String getDescription() {
        return "Detecta anomalías en las operaciones: horario_sospechoso (fuera de horario laboral), consumo_excesivo (tarjetas con desviación >30% sobre la media). Especificar tipo o 'todas'.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "tipo", Map.of("type", "string", "enum", List.of("horario_sospechoso", "consumo_excesivo", "todas"),
                    "description", "Tipo de anomalía a detectar", "default", "todas"),
                "limit", Map.of("type", "integer", "description", "Máximo de resultados", "default", 20)
            ),
            "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String tipo = args.containsKey("tipo") ? (String) args.get("tipo") : "todas";
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 20;

        List<Map<String, Object>> data = new ArrayList<>();

        if ("horario_sospechoso".equals(tipo) || "todas".equals(tipo)) {
            var horarioAnomalo = operacionRepository.findAll().stream()
                .filter(o -> o.getFechaHora() != null)
                .filter(o -> {
                    int h = o.getFechaHora().getHour();
                    DayOfWeek d = o.getFechaHora().getDayOfWeek();
                    return h < 7 || h >= 20 || d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
                })
                .limit(limit)
                .map(o -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("tipo", "horario_sospechoso");
                    row.put("id", o.getId());
                    row.put("fechaHora", o.getFechaHora().toString());
                    row.put("conductor", o.getTarjetaResumen() != null ? o.getTarjetaResumen().getConductor() : null);
                    row.put("establecimiento", o.getEstablecimiento());
                    row.put("importe", o.getImporteTotal() != null ? o.getImporteTotal().doubleValue() : 0.0);
                    row.put("motivo", detectarMotivo(o.getFechaHora()));
                    return row;
                })
                .collect(Collectors.toList());
            data.addAll(horarioAnomalo);
        }

        if ("consumo_excesivo".equals(tipo) || "todas".equals(tipo)) {
            var ops = operacionRepository.findAll();
            double globalAvg = ops.stream()
                .filter(o -> o.getImporteTotal() != null)
                .mapToDouble(o -> o.getImporteTotal().doubleValue())
                .average().orElse(0);

            ops.stream()
                .filter(o -> o.getTarjetaResumen() != null)
                .collect(Collectors.groupingBy(o -> o.getTarjetaResumen().getNumTarjeta()))
                .entrySet().stream()
                .map(e -> {
                    double cardAvg = e.getValue().stream()
                        .filter(o -> o.getImporteTotal() != null)
                        .mapToDouble(o -> o.getImporteTotal().doubleValue())
                        .average().orElse(0);
                    double desvPct = globalAvg > 0 ? ((cardAvg - globalAvg) / globalAvg) * 100 : 0;
                    String conductor = e.getValue().stream()
                        .map(o -> o.getTarjetaResumen().getConductor())
                        .filter(Objects::nonNull).findFirst().orElse(null);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("tipo", "consumo_excesivo");
                    row.put("tarjeta", e.getKey());
                    row.put("conductor", conductor);
                    row.put("mediaOperacion", Math.round(cardAvg * 100.0) / 100.0);
                    row.put("mediaGlobal", Math.round(globalAvg * 100.0) / 100.0);
                    row.put("desviacionPct", Math.round(desvPct * 10.0) / 10.0);
                    row.put("excesivo", Math.abs(desvPct) > 30);
                    return row;
                })
                .filter(r -> (boolean) r.get("excesivo"))
                .sorted((a, b) -> Double.compare(
                    Math.abs((double) b.get("desviacionPct")),
                    Math.abs((double) a.get("desviacionPct"))))
                .limit(limit)
                .forEach(data::add);
        }

        log.info("[Asistente] tool={} args={} resultRows={}", getName(), args, data.size());

        ChartSpec chart = new ChartSpec(
            "table",
            "Anomalías detectadas",
            tipo + " — " + data.size() + " resultado(s)",
            data,
            Map.of("columns", List.of("tipo", "conductor", "fechaHora", "importe", "motivo"))
        );

        return new ToolResult(getName(), args, data, chart);
    }

    private String detectarMotivo(java.time.LocalDateTime dt) {
        DayOfWeek d = dt.getDayOfWeek();
        int h = dt.getHour();
        if (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY) return "Fin de semana";
        if (h < 7) return "Madrugada (antes 7:00)";
        if (h >= 20) return "Noche (después 20:00)";
        return "Desconocido";
    }
}
