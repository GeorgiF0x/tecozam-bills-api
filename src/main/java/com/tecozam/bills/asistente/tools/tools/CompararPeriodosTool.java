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
public class CompararPeriodosTool implements Tool {

    private final OperacionRepository operacionRepository;

    @Override
    public String getName() {
        return "comparar_periodos";
    }

    @Override
    public String getDescription() {
        return "Compara el gasto por concepto entre dos períodos de tiempo. Útil para detectar incrementos o ahorros.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "periodoA_from", Map.of("type", "string", "format", "date", "description", "Período A — fecha desde"),
                "periodoA_to", Map.of("type", "string", "format", "date", "description", "Período A — fecha hasta"),
                "periodoB_from", Map.of("type", "string", "format", "date", "description", "Período B — fecha desde"),
                "periodoB_to", Map.of("type", "string", "format", "date", "description", "Período B — fecha hasta")
            ),
            "required", List.of("periodoA_from", "periodoA_to", "periodoB_from", "periodoB_to")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        LocalDate aFrom = LocalDate.parse((String) args.get("periodoA_from"));
        LocalDate aTo = LocalDate.parse((String) args.get("periodoA_to"));
        LocalDate bFrom = LocalDate.parse((String) args.get("periodoB_from"));
        LocalDate bTo = LocalDate.parse((String) args.get("periodoB_to"));

        Map<String, Double> gastoA = gastoByConcepto(aFrom.atStartOfDay(), aTo.atTime(LocalTime.MAX));
        Map<String, Double> gastoB = gastoByConcepto(bFrom.atStartOfDay(), bTo.atTime(LocalTime.MAX));

        Set<String> conceptos = new TreeSet<>();
        conceptos.addAll(gastoA.keySet());
        conceptos.addAll(gastoB.keySet());

        List<Map<String, Object>> data = conceptos.stream().map(c -> {
            double va = gastoA.getOrDefault(c, 0.0);
            double vb = gastoB.getOrDefault(c, 0.0);
            double diff = vb - va;
            double diffPct = va > 0 ? (diff / va) * 100 : 0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("categoria", c);
            row.put("periodoA", Math.round(va * 100.0) / 100.0);
            row.put("periodoB", Math.round(vb * 100.0) / 100.0);
            row.put("diferencia", Math.round(diff * 100.0) / 100.0);
            row.put("diferenciaPct", Math.round(diffPct * 10.0) / 10.0);
            return row;
        }).collect(Collectors.toList());

        log.info("[Asistente] tool={} args={} resultRows={}", getName(), args, data.size());

        String subtitle = "A: " + aFrom + " — " + aTo + " vs B: " + bFrom + " — " + bTo;
        ChartSpec chart = new ChartSpec(
            "bar",
            "Comparativa de períodos",
            subtitle,
            data,
            Map.of("xKey", "categoria", "yKeys", List.of("periodoA", "periodoB"), "grouped", true)
        );

        return new ToolResult(getName(), args, data, chart);
    }

    private Map<String, Double> gastoByConcepto(LocalDateTime from, LocalDateTime to) {
        return operacionRepository.findAll().stream()
            .filter(o -> o.getFechaHora() != null
                && !o.getFechaHora().isBefore(from)
                && !o.getFechaHora().isAfter(to))
            .collect(Collectors.groupingBy(
                o -> o.getConceptoUnificado() != null ? o.getConceptoUnificado() : "OTRO",
                Collectors.summingDouble(o -> o.getImporteTotal() != null ? o.getImporteTotal().doubleValue() : 0.0)
            ));
    }
}
