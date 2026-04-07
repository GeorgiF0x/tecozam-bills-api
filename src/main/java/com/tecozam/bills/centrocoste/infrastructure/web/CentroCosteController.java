package com.tecozam.bills.centrocoste.infrastructure.web;

import com.tecozam.bills.centrocoste.application.CentroCosteService;
import com.tecozam.bills.centrocoste.dto.CentroCosteDTO;
import com.tecozam.bills.centrocoste.dto.CreateCentroCosteRequest;
import com.tecozam.bills.centrocoste.dto.UpdateCentroCosteRequest;
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
@RequestMapping("/api/centros-coste")
@RequiredArgsConstructor
@Tag(name = "Centros de Coste")
public class CentroCosteController {

    private final CentroCosteService centroCosteService;

    @GetMapping
    public List<CentroCosteDTO> findAll(@RequestParam(defaultValue = "true") boolean activo) {
        return centroCosteService.findAll(activo);
    }

    @GetMapping("/{id}")
    public CentroCosteDTO findById(@PathVariable Long id) {
        return centroCosteService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public ResponseEntity<CentroCosteDTO> create(@Valid @RequestBody CreateCentroCosteRequest request) {
        return ResponseEntity.status(201).body(centroCosteService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public CentroCosteDTO update(@PathVariable Long id, @Valid @RequestBody UpdateCentroCosteRequest request) {
        return centroCosteService.update(id, request);
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> toggleActivo(@PathVariable Long id) {
        centroCosteService.toggleActivo(id);
        return ResponseEntity.noContent().build();
    }
}
