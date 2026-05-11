package com.tecozam.bills.auth.infrastructure.web;

import com.tecozam.bills.auth.application.UsuarioOficinaService;
import com.tecozam.bills.auth.dto.UsuarioOficinaDTO;
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
@RequestMapping("/api/usuarios/oficina")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Usuarios Oficina", description = "Gestión de usuarios de oficina (solo ADMIN)")
public class UsuarioOficinaController {

    private final UsuarioOficinaService usuarioOficinaService;

    @GetMapping
    @Operation(summary = "Listar usuarios de oficina")
    public ResponseEntity<List<UsuarioOficinaDTO>> findAll() {
        return ResponseEntity.ok(usuarioOficinaService.findAll());
    }

    @GetMapping("/pendientes")
    @Operation(summary = "Listar usuarios de oficina pendientes de activación")
    public ResponseEntity<List<UsuarioOficinaDTO>> findPendientes() {
        return ResponseEntity.ok(usuarioOficinaService.findPendientes());
    }

    @PatchMapping("/{id}/activar")
    @Operation(summary = "Activar usuario de oficina")
    public ResponseEntity<UsuarioOficinaDTO> activar(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioOficinaService.activar(id));
    }

    @PatchMapping("/{id}/rechazar")
    @Operation(summary = "Rechazar usuario de oficina")
    public ResponseEntity<UsuarioOficinaDTO> rechazar(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioOficinaService.rechazar(id));
    }

    @PatchMapping("/{id}/desactivar")
    @Operation(summary = "Desactivar usuario de oficina")
    public ResponseEntity<UsuarioOficinaDTO> desactivar(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioOficinaService.desactivar(id));
    }
}
