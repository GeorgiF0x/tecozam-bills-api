package com.tecozam.bills.tarifa.infrastructure.web;

import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.tarifa.domain.Tarifa;
import com.tecozam.bills.tarifa.domain.TarifaPrecio;
import com.tecozam.bills.tarifa.dto.*;
import com.tecozam.bills.tarifa.infrastructure.persistence.TarifaRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tarifas")
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Tag(name = "Tarifas", description = "Gestión de tarifas de proveedores de combustible")
public class TarifaController {

    private final TarifaRepository tarifaRepository;
    private final ProveedorRepository proveedorRepository;

    @GetMapping
    public List<TarifaDTO> findAll(@RequestParam(required = false) Long proveedorId) {
        List<Tarifa> tarifas = proveedorId != null
                ? tarifaRepository.findByProveedorIdOrderByVigenteDesdeDesc(proveedorId)
                : tarifaRepository.findAll();
        return tarifas.stream().map(this::toDTO).toList();
    }

    @GetMapping("/{id}")
    public TarifaDTO findById(@PathVariable Long id) {
        return toDTO(tarifaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tarifa", id)));
    }

    @GetMapping("/vigente")
    public ResponseEntity<TarifaDTO> findVigente(
            @RequestParam Long proveedorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return tarifaRepository.findVigenteEnFecha(proveedorId, fecha)
                .map(t -> ResponseEntity.ok(toDTO(t)))
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public ResponseEntity<TarifaDTO> create(@Valid @RequestBody CreateTarifaRequest request) {
        Proveedor proveedor = proveedorRepository.findById(request.proveedorId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", request.proveedorId()));

        // Close previous tariff if open-ended
        tarifaRepository.findVigenteEnFecha(request.proveedorId(), request.vigenteDesde())
                .ifPresent(prev -> {
                    if (prev.getVigenteHasta() == null) {
                        prev.setVigenteHasta(request.vigenteDesde().minusDays(1));
                        tarifaRepository.save(prev);
                    }
                });

        Tarifa tarifa = Tarifa.builder()
                .proveedor(proveedor)
                .codigoTarifa(request.codigoTarifa())
                .vigenteDesde(request.vigenteDesde())
                .vigenteHasta(request.vigenteHasta())
                .observaciones(request.observaciones())
                .build();

        for (var p : request.precios()) {
            TarifaPrecio precio = TarifaPrecio.builder()
                    .tarifa(tarifa)
                    .producto(p.producto())
                    .conceptoUnificado(p.conceptoUnificado())
                    .precioSinIva(p.precioSinIva())
                    .precioConIva(p.precioConIva())
                    .build();
            tarifa.getPrecios().add(precio);
        }

        tarifa = tarifaRepository.save(tarifa);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(tarifa));
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tarifaRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private TarifaDTO toDTO(Tarifa t) {
        return new TarifaDTO(
                t.getId(),
                t.getProveedor().getId(),
                t.getProveedor().getNombre(),
                t.getCodigoTarifa(),
                t.getVigenteDesde(),
                t.getVigenteHasta(),
                t.getObservaciones(),
                t.getPrecios().stream().map(p -> new TarifaPrecioDTO(
                        p.getId(), p.getProducto(), p.getConceptoUnificado(),
                        p.getPrecioSinIva(), p.getPrecioConIva()
                )).toList(),
                t.getCreadoEn()
        );
    }
}
