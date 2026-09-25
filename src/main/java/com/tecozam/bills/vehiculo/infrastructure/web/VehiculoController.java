package com.tecozam.bills.vehiculo.infrastructure.web;

import com.tecozam.bills.vehiculo.application.VehiculoService;
import com.tecozam.bills.vehiculo.dto.CreateVehiculoRequest;
import com.tecozam.bills.vehiculo.dto.UpdateVehiculoRequest;
import com.tecozam.bills.vehiculo.dto.VehiculoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vehiculos")
@RequiredArgsConstructor
@Tag(name = "Vehículos")
public class VehiculoController {

    private final VehiculoService vehiculoService;

    @GetMapping
    public List<VehiculoDTO> findAll(@RequestParam(defaultValue = "true") boolean activo) {
        return vehiculoService.findAll(activo);
    }

    @GetMapping("/{id}")
    public VehiculoDTO findById(@PathVariable Long id) {
        return vehiculoService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public ResponseEntity<VehiculoDTO> create(@Valid @RequestBody CreateVehiculoRequest request) {
        return ResponseEntity.status(201).body(vehiculoService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public VehiculoDTO update(@PathVariable Long id, @Valid @RequestBody UpdateVehiculoRequest request) {
        return vehiculoService.update(id, request);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public ResponseEntity<Void> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        vehiculoService.cambiarEstado(id, body.get("estado"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar vehículo", description = "Por defecto, borrado lógico (se conserva para auditoría). Con real=true, borrado físico si no tiene historial asociado")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean real) {
        vehiculoService.eliminar(id, real);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar vehículos en masa", description = "Por defecto, borrado lógico en masa. Con real=true, borrado físico si ninguno tiene historial asociado")
    public ResponseEntity<Void> eliminarMasivo(@RequestBody List<Long> ids, @RequestParam(defaultValue = "false") boolean real) {
        vehiculoService.eliminarMasivo(ids, real);
        return ResponseEntity.noContent().build();
    }
}
