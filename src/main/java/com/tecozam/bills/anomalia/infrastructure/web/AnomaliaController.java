package com.tecozam.bills.anomalia.infrastructure.web;

import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import com.tecozam.bills.factura.domain.Operacion;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;
import java.util.stream.*;

@RestController
@Transactional(readOnly = true)
@RequestMapping("/api/anomalias")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
@Tag(name = "Anomalías", description = "Detección de anomalías en operaciones")
public class AnomaliaController {

    private final OperacionRepository operacionRepository;

    // Returns operations outside work hours (before 7:00 or after 20:00, or weekends)
    @GetMapping("/horario-sospechoso")
    public List<Map<String, Object>> findHorarioSospechoso() {
        return operacionRepository.findAll().stream()
            .filter(o -> o.getFechaHora() != null)
            .filter(o -> {
                LocalDateTime dt = o.getFechaHora();
                int hour = dt.getHour();
                DayOfWeek day = dt.getDayOfWeek();
                return hour < 7 || hour >= 20 || day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            })
            .map(o -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", o.getId());
                m.put("fechaHora", o.getFechaHora());
                m.put("establecimiento", o.getEstablecimiento());
                m.put("conceptoUnificado", o.getConceptoUnificado());
                m.put("importeTotal", o.getImporteTotal());
                m.put("tarjetaNumero", o.getTarjetaResumen() != null ? o.getTarjetaResumen().getNumTarjeta() : null);
                m.put("conductor", o.getTarjetaResumen() != null ? o.getTarjetaResumen().getConductor() : null);
                m.put("motivo", detectMotivo(o.getFechaHora()));
                return m;
            })
            .collect(Collectors.toList());
    }

    // Returns per-card spend summary for detecting excessive consumption
    @GetMapping("/consumo-excesivo")
    public List<Map<String, Object>> findConsumoExcesivo() {
        List<Operacion> ops = operacionRepository.findAll();

        // Group by tarjeta, compute total and count
        Map<String, List<Operacion>> byTarjeta = ops.stream()
            .filter(o -> o.getTarjetaResumen() != null)
            .collect(Collectors.groupingBy(o -> o.getTarjetaResumen().getNumTarjeta()));

        // Compute global average across all operations
        double globalAvg = ops.stream()
            .filter(o -> o.getImporteTotal() != null)
            .mapToDouble(o -> o.getImporteTotal().doubleValue())
            .average().orElse(0);

        List<Map<String, Object>> results = new ArrayList<>();

        for (var entry : byTarjeta.entrySet()) {
            double cardTotal = entry.getValue().stream()
                .filter(o -> o.getImporteTotal() != null)
                .mapToDouble(o -> o.getImporteTotal().doubleValue())
                .sum();
            double cardAvg = entry.getValue().stream()
                .filter(o -> o.getImporteTotal() != null)
                .mapToDouble(o -> o.getImporteTotal().doubleValue())
                .average().orElse(0);
            int count = entry.getValue().size();
            double totalLitros = entry.getValue().stream()
                .filter(o -> o.getCantidad() != null)
                .mapToDouble(o -> o.getCantidad().doubleValue())
                .sum();
            String conductor = entry.getValue().stream()
                .filter(o -> o.getTarjetaResumen() != null)
                .map(o -> o.getTarjetaResumen().getConductor())
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

            double desvPct = globalAvg > 0 ? ((cardAvg - globalAvg) / globalAvg) * 100 : 0;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tarjeta", entry.getKey());
            m.put("conductor", conductor);
            m.put("numOperaciones", count);
            m.put("totalGasto", cardTotal);
            m.put("mediaOperacion", cardAvg);
            m.put("mediaGlobal", globalAvg);
            m.put("desviacionPct", Math.round(desvPct * 10.0) / 10.0);
            m.put("totalLitros", totalLitros);
            m.put("excesivo", Math.abs(desvPct) > 30);
            results.add(m);
        }

        results.sort((a, b) -> Double.compare(
            Math.abs((double) b.get("desviacionPct")),
            Math.abs((double) a.get("desviacionPct"))
        ));
        return results;
    }

    private String detectMotivo(LocalDateTime dt) {
        DayOfWeek day = dt.getDayOfWeek();
        int hour = dt.getHour();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return "Fin de semana";
        if (hour < 7) return "Madrugada (antes de las 7:00)";
        if (hour >= 20) return "Noche (después de las 20:00)";
        return "Desconocido";
    }
}
