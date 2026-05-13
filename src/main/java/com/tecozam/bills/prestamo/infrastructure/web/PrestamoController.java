package com.tecozam.bills.prestamo.infrastructure.web;

import com.tecozam.bills.prestamo.application.PrestamoService;
import com.tecozam.bills.prestamo.dto.CreateMiPrestamoRequest;
import com.tecozam.bills.prestamo.dto.CreatePrestamoRequest;
import com.tecozam.bills.prestamo.dto.DevolucionRequest;
import com.tecozam.bills.prestamo.dto.PrestamoDTO;
import com.tecozam.bills.prestamo.dto.RecursoDisponibleDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/prestamos")
@RequiredArgsConstructor
@Tag(name = "Préstamos", description = "Gestión de préstamos de recursos")
public class PrestamoController {

    private final PrestamoService prestamoService;

    @GetMapping
    @Operation(summary = "Listar préstamos", description = "Obtiene todos los préstamos, opcionalmente filtrados por estado")
    public ResponseEntity<List<PrestamoDTO>> findAll(
            @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(prestamoService.findAll(estado));
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Obtener préstamo", description = "Obtiene un préstamo por su ID")
    public ResponseEntity<PrestamoDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(prestamoService.findById(id));
    }

    @GetMapping("/trabajador/{trabajadorId}")
    @Operation(summary = "Préstamos por trabajador", description = "Obtiene todos los préstamos de un trabajador")
    public ResponseEntity<List<PrestamoDTO>> findByTrabajador(@PathVariable Long trabajadorId) {
        return ResponseEntity.ok(prestamoService.findByTrabajador(trabajadorId));
    }

    @GetMapping("/mis-prestamos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mis préstamos", description = "Devuelve los préstamos del trabajador autenticado.")
    public ResponseEntity<List<PrestamoDTO>> misPrestamos(Authentication authentication) {
        return ResponseEntity.ok(prestamoService.findMisPrestamos(authentication.getName()));
    }

    @GetMapping("/recursos-disponibles")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Recursos disponibles", description = "Devuelve los recursos del tipo indicado en estado DISPONIBLE. Tipo: TARJETA | VIAT | VEHICULO.")
    public ResponseEntity<List<RecursoDisponibleDTO>> recursosDisponibles(@RequestParam String tipo) {
        return ResponseEntity.ok(prestamoService.findRecursosDisponibles(tipo));
    }

    @PostMapping("/mis-prestamos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Crear préstamo self-service", description = "El operario se auto-asigna un recurso disponible. El trabajador se resuelve del JWT.")
    public ResponseEntity<PrestamoDTO> crearMiPrestamo(
            @Valid @RequestBody CreateMiPrestamoRequest request,
            Authentication authentication) {
        PrestamoDTO created = prestamoService.crearMiPrestamo(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/mis-devoluciones")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Devolver mi préstamo", description = "Cierra un préstamo activo del trabajador autenticado y libera el recurso.")
    public ResponseEntity<PrestamoDTO> miDevolucion(
            @PathVariable Long id,
            @RequestBody(required = false) DevolucionRequest request,
            Authentication authentication) {
        String obs = request != null ? request.observaciones() : null;
        return ResponseEntity.ok(prestamoService.miDevolucion(authentication.getName(), id, obs));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    @Operation(summary = "Crear préstamo", description = "Crea un nuevo préstamo de recurso")
    public ResponseEntity<PrestamoDTO> create(@Valid @RequestBody CreatePrestamoRequest request) {
        PrestamoDTO created = prestamoService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/devolucion")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    @Operation(summary = "Registrar devolución", description = "Registra la devolución de un préstamo activo")
    public ResponseEntity<PrestamoDTO> registrarDevolucion(
            @PathVariable Long id,
            @RequestBody(required = false) DevolucionRequest request) {
        return ResponseEntity.ok(prestamoService.registrarDevolucion(id, request));
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cancelar préstamo", description = "Cancela un préstamo activo")
    public ResponseEntity<Void> cancelar(@PathVariable Long id) {
        prestamoService.cancelar(id);
        return ResponseEntity.noContent().build();
    }
}
