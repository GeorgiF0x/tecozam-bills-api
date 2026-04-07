package com.tecozam.bills.viat.infrastructure.web;

import com.tecozam.bills.viat.application.ViatService;
import com.tecozam.bills.viat.dto.CreateViatRequest;
import com.tecozam.bills.viat.dto.UpdateViatRequest;
import com.tecozam.bills.viat.dto.ViatDTO;
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
import java.util.Map;

@RestController
@RequestMapping("/api/viats")
@RequiredArgsConstructor
@Tag(name = "Viats")
public class ViatController {

    private final ViatService viatService;

    @GetMapping
    public List<ViatDTO> findAll(@RequestParam(defaultValue = "true") boolean activo) {
        return viatService.findAll(activo);
    }

    @GetMapping("/{id}")
    public ViatDTO findById(@PathVariable Long id) {
        return viatService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public ResponseEntity<ViatDTO> create(@Valid @RequestBody CreateViatRequest request) {
        return ResponseEntity.status(201).body(viatService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public ViatDTO update(@PathVariable Long id, @Valid @RequestBody UpdateViatRequest request) {
        return viatService.update(id, request);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public ResponseEntity<Void> cambiarEstado(@PathVariable Long id, @RequestBody Map<String, String> body) {
        viatService.cambiarEstado(id, body.get("estado"));
        return ResponseEntity.noContent().build();
    }
}
