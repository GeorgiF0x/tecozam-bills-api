package com.tecozam.bills.auth.infrastructure.web;

import com.tecozam.bills.auth.application.UsuarioCampoService;
import com.tecozam.bills.auth.dto.UsuarioCampoDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/usuarios/campo")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Usuarios Campo", description = "Gestión de usuarios de campo (solo ADMIN)")
public class UsuarioCampoController {

    private final UsuarioCampoService usuarioCampoService;

    @GetMapping
    @Operation(summary = "Listar usuarios de campo")
    public ResponseEntity<List<UsuarioCampoDTO>> findAll() {
        return ResponseEntity.ok(usuarioCampoService.findAll());
    }

    @GetMapping("/pendientes")
    @Operation(summary = "Listar usuarios de campo pendientes de activación")
    public ResponseEntity<List<UsuarioCampoDTO>> findPendientes() {
        return ResponseEntity.ok(usuarioCampoService.findPendientes());
    }

    @PatchMapping("/{id}/activar")
    @Operation(summary = "Activar usuario de campo")
    public ResponseEntity<UsuarioCampoDTO> activar(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioCampoService.activar(id));
    }

    @PatchMapping("/{id}/rechazar")
    @Operation(summary = "Rechazar usuario de campo")
    public ResponseEntity<UsuarioCampoDTO> rechazar(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioCampoService.rechazar(id));
    }

    @PatchMapping("/{id}/desactivar")
    @Operation(summary = "Desactivar usuario de campo")
    public ResponseEntity<UsuarioCampoDTO> desactivar(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioCampoService.desactivar(id));
    }
}
