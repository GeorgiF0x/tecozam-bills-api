package com.tecozam.bills.asistente.tools.tools;

import com.tecozam.bills.asistente.tools.ChartSpec;
import com.tecozam.bills.asistente.tools.Tool;
import com.tecozam.bills.asistente.tools.ToolResult;
import com.tecozam.bills.factura.infrastructure.persistence.FacturaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class GastoPorProveedorTool implements Tool {

    private final FacturaRepository facturaRepository;

    @Override
    public String getName() {
        return "get_gasto_por_proveedor";
    }

    @Override
    public String getDescription() {
        return "Devuelve el gasto total agrupado por proveedor (MOEVE, REPSOL, etc.) en un rango de fechas.";
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

        List<Map<String, Object>> raw = facturaRepository.findAll().stream()
            .filter(f -> f.getFecha() != null
                && !f.getFecha().isBefore(from)
                && !f.getFecha().isAfter(to)
                && f.getProveedor() != null)
            .collect(Collectors.groupingBy(f -> f.getProveedor().getNombre()))
            .entrySet().stream()
            .map(e -> {
                double importe = e.getValue().stream()
                    .filter(f -> f.getTotalFactura() != null)
                    .mapToDouble(f -> f.getTotalFactura().doubleValue())
                    .sum();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("proveedor", e.getKey());
                row.put("importe", Math.round(importe * 100.0) / 100.0);
                row.put("numFacturas", e.getValue().size());
                return row;
            })
            .sorted((a, b) -> Double.compare((double) b.get("importe"), (double) a.get("importe")))
            .collect(Collectors.toList());

        double total = raw.stream().mapToDouble(r -> (double) r.get("importe")).sum();
        List<Map<String, Object>> data = raw.stream().map(r -> {
            double pct = total > 0 ? ((double) r.get("importe") / total) * 100 : 0;
            Map<String, Object> row = new LinkedHashMap<>(r);
            row.put("percent", Math.round(pct * 10.0) / 10.0);
            return row;
        }).collect(Collectors.toList());

        log.info("[Asistente] tool={} args={} resultRows={}", getName(), args, data.size());

        ChartSpec chart = new ChartSpec(
            "pie",
            "Gasto por proveedor",
            "Del " + from + " al " + to,
            data,
            Map.of("nameKey", "proveedor", "valueKey", "importe")
        );

        return new ToolResult(getName(), args, data, chart);
    }
}
