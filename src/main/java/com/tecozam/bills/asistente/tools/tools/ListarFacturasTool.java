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
public class ListarFacturasTool implements Tool {

    private final FacturaRepository facturaRepository;

    @Override
    public String getName() {
        return "listar_facturas";
    }

    @Override
    public String getDescription() {
        return "Lista las facturas del sistema con filtros opcionales por rango de fechas y proveedor.";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "from", Map.of("type", "string", "format", "date", "description", "Fecha desde YYYY-MM-DD (opcional)"),
                "to", Map.of("type", "string", "format", "date", "description", "Fecha hasta YYYY-MM-DD (opcional)"),
                "proveedor", Map.of("type", "string", "description", "Nombre del proveedor para filtrar (opcional)"),
                "limit", Map.of("type", "integer", "description", "Máximo de facturas a devolver", "default", 20)
            ),
            "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        LocalDate from = args.containsKey("from") ? LocalDate.parse((String) args.get("from")) : null;
        LocalDate to = args.containsKey("to") ? LocalDate.parse((String) args.get("to")) : null;
        String proveedorFiltro = args.containsKey("proveedor") ? ((String) args.get("proveedor")).toLowerCase() : null;
        int limit = args.containsKey("limit") ? ((Number) args.get("limit")).intValue() : 20;

        List<Map<String, Object>> data = facturaRepository.findAll().stream()
            .filter(f -> from == null || (f.getFecha() != null && !f.getFecha().isBefore(from)))
            .filter(f -> to == null || (f.getFecha() != null && !f.getFecha().isAfter(to)))
            .filter(f -> proveedorFiltro == null || (f.getProveedor() != null
                && f.getProveedor().getNombre().toLowerCase().contains(proveedorFiltro)))
            .sorted((a, b) -> {
                if (a.getFecha() == null && b.getFecha() == null) return 0;
                if (a.getFecha() == null) return 1;
                if (b.getFecha() == null) return -1;
                return b.getFecha().compareTo(a.getFecha());
            })
            .limit(limit)
            .map(f -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", f.getId());
                row.put("numFactura", f.getNumFactura());
                row.put("fecha", f.getFecha() != null ? f.getFecha().toString() : null);
                row.put("proveedor", f.getProveedor() != null ? f.getProveedor().getNombre() : null);
                row.put("importe", f.getTotalFactura() != null ? f.getTotalFactura().doubleValue() : 0.0);
                row.put("baseImponible", f.getBaseImponible() != null ? f.getBaseImponible().doubleValue() : 0.0);
                row.put("periodoDesde", f.getPeriodoDesde() != null ? f.getPeriodoDesde().toString() : null);
                row.put("periodoHasta", f.getPeriodoHasta() != null ? f.getPeriodoHasta().toString() : null);
                return row;
            })
            .collect(Collectors.toList());

        log.info("[Asistente] tool={} args={} resultRows={}", getName(), args, data.size());

        ChartSpec chart = new ChartSpec(
            "table",
            "Listado de facturas",
            data.size() + " factura(s)",
            data,
            Map.of("columns", List.of("numFactura", "fecha", "proveedor", "importe"))
        );

        return new ToolResult(getName(), args, data, chart);
    }
}
