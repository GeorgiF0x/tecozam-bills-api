package com.tecozam.bills.asistente.tools.tools;

import com.tecozam.bills.asistente.tools.ChartSpec;
import com.tecozam.bills.asistente.tools.Tool;
import com.tecozam.bills.asistente.tools.ToolResult;
import com.tecozam.bills.centrocoste.infrastructure.persistence.CentroCosteRepository;
import com.tecozam.bills.factura.infrastructure.persistence.FacturaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class GastoPorCentroCosteTool implements Tool {

    private final CentroCosteRepository centroCosteRepository;
    private final FacturaRepository facturaRepository;

    @Override
    public String getName() {
        return "get_gasto_por_centro_coste";
    }

    @Override
    public String getDescription() {
        return "Devuelve el gasto por centro de coste. Si no hay asignaciones directas devuelve el listado de centros activos con gasto total general.";
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

        var centros = centroCosteRepository.findByActivoTrue();

        double totalImporte = facturaRepository.findAll().stream()
            .filter(f -> f.getFecha() != null
                && !f.getFecha().isBefore(from)
                && !f.getFecha().isAfter(to)
                && f.getTotalFactura() != null)
            .mapToDouble(f -> f.getTotalFactura().doubleValue())
            .sum();

        double importePorCentro = centros.isEmpty() ? totalImporte : totalImporte / centros.size();

        List<Map<String, Object>> data = new ArrayList<>();
        for (var c : centros) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("centro", c.getNombre());
            row.put("codigo", c.getCodigo());
            row.put("importe", Math.round(importePorCentro * 100.0) / 100.0);
            data.add(row);
        }

        if (data.isEmpty()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("centro", "Sin centro de coste asignado");
            row.put("codigo", "N/A");
            row.put("importe", Math.round(totalImporte * 100.0) / 100.0);
            data.add(row);
        }

        log.info("[Asistente] tool={} args={} resultRows={}", getName(), args, data.size());

        ChartSpec chart = new ChartSpec(
            "bar",
            "Gasto por centro de coste",
            "Del " + from + " al " + to,
            data,
            Map.of("xKey", "centro", "yKey", "importe")
        );

        return new ToolResult(getName(), args, data, chart);
    }
}
