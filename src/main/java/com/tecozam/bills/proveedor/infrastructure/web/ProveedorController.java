package com.tecozam.bills.proveedor.infrastructure.web;

import com.tecozam.bills.proveedor.application.ProveedorService;
import com.tecozam.bills.proveedor.dto.CreateProveedorRequest;
import com.tecozam.bills.proveedor.dto.ProveedorDTO;
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

@RestController
@RequestMapping("/api/proveedores")
@RequiredArgsConstructor
@Tag(name = "Proveedores")
public class ProveedorController {

    private final ProveedorService proveedorService;

    @GetMapping
    public List<ProveedorDTO> findAll() {
        return proveedorService.findAll();
    }

    @GetMapping("/todos")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ProveedorDTO> findTodos() {
        return proveedorService.findTodos();
    }

    @GetMapping("/{id}")
    public ProveedorDTO findById(@PathVariable Long id) {
        return proveedorService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProveedorDTO> create(@Valid @RequestBody CreateProveedorRequest request) {
        return ResponseEntity.status(201).body(proveedorService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProveedorDTO update(@PathVariable Long id, @Valid @RequestBody CreateProveedorRequest request) {
        return proveedorService.update(id, request);
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> toggleActivo(@PathVariable Long id) {
        proveedorService.toggleActivo(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar proveedor", description = "Por defecto, borrado lógico (se conserva para auditoría). Con real=true, borrado físico si no tiene historial asociado")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean real) {
        proveedorService.eliminar(id, real);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Eliminar proveedores en masa", description = "Por defecto, borrado lógico en masa. Con real=true, borrado físico si ninguno tiene historial asociado")
    public ResponseEntity<Void> eliminarMasivo(@RequestBody List<Long> ids, @RequestParam(defaultValue = "false") boolean real) {
        proveedorService.eliminarMasivo(ids, real);
        return ResponseEntity.noContent().build();
    }
}
