package com.tecozam.bills.trabajador.infrastructure.web;

import com.tecozam.bills.trabajador.application.FusionarTrabajadoresService;
import com.tecozam.bills.trabajador.application.TrabajadorService;
import com.tecozam.bills.trabajador.dto.CreateTrabajadorRequest;
import com.tecozam.bills.trabajador.dto.FusionarTrabajadoresResponse;
import com.tecozam.bills.trabajador.dto.TrabajadorDTO;
import com.tecozam.bills.trabajador.dto.UpdateTrabajadorRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/trabajadores")
@RequiredArgsConstructor
@Tag(name = "Trabajadores")
public class TrabajadorController {

    private final TrabajadorService trabajadorService;
    private final FusionarTrabajadoresService fusionarService;

    @GetMapping
    public List<TrabajadorDTO> findAll(@RequestParam(defaultValue = "true") boolean activo) {
        return trabajadorService.findAll(activo);
    }

    @GetMapping("/{id}")
    public TrabajadorDTO findById(@PathVariable Long id) {
        return trabajadorService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public ResponseEntity<TrabajadorDTO> create(@Valid @RequestBody CreateTrabajadorRequest request) {
        return ResponseEntity.status(201).body(trabajadorService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public TrabajadorDTO update(@PathVariable Long id, @Valid @RequestBody UpdateTrabajadorRequest request) {
        return trabajadorService.update(id, request);
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> toggleActivo(@PathVariable Long id) {
        trabajadorService.toggleActivo(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * BILLS-10: fusiona dos trabajadores duplicados. Re-vincula tarjetas,
     * tickets, prestamos y usuarios del perdedor al ganador y borra el
     * perdedor. Devuelve los contadores de filas movidas.
     */
    @PostMapping("/{ganadorId}/fusionar/{perdedorId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FusionarTrabajadoresResponse> fusionar(
            @PathVariable Long ganadorId,
            @PathVariable Long perdedorId) {
        return ResponseEntity.ok(fusionarService.fusionar(ganadorId, perdedorId));
    }
}
