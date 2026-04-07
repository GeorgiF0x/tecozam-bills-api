package com.tecozam.bills.factura.infrastructure.web;

import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.dto.OperacionDTO;
import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/operaciones")
@RequiredArgsConstructor
@Tag(name = "Operaciones", description = "Consulta de operaciones importadas de facturas")
public class OperacionController {

    private final OperacionRepository operacionRepository;

    /**
     * Lista operaciones con filtros opcionales y paginación.
     *
     * @param page             número de página (0-indexed, default 0)
     * @param size             tamaño de página (default 50)
     * @param proveedorId      filtro por proveedor (opcional)
     * @param conceptoUnificado filtro por concepto unificado (opcional, ej: DIESEL, PEAJE)
     * @param fechaDesde       filtro fecha desde (ISO date, opcional)
     * @param fechaHasta       filtro fecha hasta (ISO date, opcional)
     */
    @GetMapping
    public Page<OperacionDTO> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long proveedorId,
            @RequestParam(required = false) String conceptoUnificado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta
    ) {
        Pageable pageable = PageRequest.of(page, size);

        LocalDateTime desde = fechaDesde != null ? fechaDesde.atStartOfDay() : null;
        LocalDateTime hasta = fechaHasta != null ? fechaHasta.atTime(23, 59, 59) : null;

        return operacionRepository
                .findWithFiltersEager(proveedorId, conceptoUnificado, desde, hasta, pageable)
                .map(this::toDTO);
    }

    /**
     * Lista todas las operaciones pertenecientes a una factura específica.
     */
    @GetMapping("/factura/{facturaId}")
    public List<OperacionDTO> findByFactura(@PathVariable Long facturaId) {
        return operacionRepository.findByFacturaId(facturaId).stream()
                .map(this::toDTO)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private OperacionDTO toDTO(Operacion o) {
        String tarjetaNumero = null;
        String conductor = null;
        String proveedorNombre = null;

        if (o.getTarjetaResumen() != null) {
            tarjetaNumero = o.getTarjetaResumen().getNumTarjeta();
            conductor     = o.getTarjetaResumen().getConductor();

            if (o.getTarjetaResumen().getFactura() != null
                    && o.getTarjetaResumen().getFactura().getProveedor() != null) {
                proveedorNombre = o.getTarjetaResumen().getFactura().getProveedor().getNombre();
            }
        }

        return new OperacionDTO(
                o.getId(),
                o.getReferencia(),
                o.getFechaHora(),
                o.getConceptoOriginal(),
                o.getConceptoUnificado(),
                o.getEstablecimiento(),
                o.getKms(),
                o.getCantidad(),
                o.getPrecioNeto(),
                o.getPrecioIvaInc(),
                o.getImporte(),
                o.getImporteTotal(),
                o.getDtoPorcentaje(),
                o.getBonificacion(),
                tarjetaNumero,
                conductor,
                proveedorNombre
        );
    }
}
