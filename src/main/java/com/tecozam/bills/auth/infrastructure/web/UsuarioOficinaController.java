package com.tecozam.bills.auth.infrastructure.web;

import com.tecozam.bills.auth.application.UsuarioOficinaService;
import com.tecozam.bills.auth.dto.CambiarRolRequest;
import com.tecozam.bills.auth.dto.CrearUsuarioOficinaRequest;
import com.tecozam.bills.auth.dto.EditarUsuarioOficinaRequest;
import com.tecozam.bills.auth.dto.InvitacionPendienteDTO;
import com.tecozam.bills.auth.dto.InvitarUsuarioRequest;
import com.tecozam.bills.auth.dto.ResetPasswordRequest;
import com.tecozam.bills.auth.dto.UsuarioOficinaDTO;
import com.tecozam.bills.shared.domain.enums.Rol;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/usuarios/oficina")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Usuarios Oficina", description = "Gestión de usuarios de oficina (solo ADMIN)")
public class UsuarioOficinaController {

    private final UsuarioOficinaService usuarioOficinaService;

    @PostMapping
    @Operation(summary = "Crear usuario de oficina (admin)",
            description = "Crea un usuario de oficina directamente en estado ACTIVO. Solo ADMIN.")
    public ResponseEntity<UsuarioOficinaDTO> crear(
            @Valid @RequestBody CrearUsuarioOficinaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(usuarioOficinaService.crear(request));
    }

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

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un usuario de oficina por id")
    public ResponseEntity<UsuarioOficinaDTO> findById(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioOficinaService.findById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Editar usuario de oficina",
            description = "Actualiza email, nombre y DNI del usuario. Solo ADMIN.")
    public ResponseEntity<UsuarioOficinaDTO> editar(
            @PathVariable Long id,
            @Valid @RequestBody EditarUsuarioOficinaRequest request) {
        return ResponseEntity.ok(usuarioOficinaService.editar(id, request));
    }

    @PatchMapping("/{id}/password")
    @Operation(summary = "Resetear contraseña de usuario de oficina (admin)")
    public ResponseEntity<UsuarioOficinaDTO> resetearPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(
                usuarioOficinaService.resetearPassword(id, request.nuevaPassword()));
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

    @PatchMapping("/{id}/rol")
    @Operation(summary = "Cambiar rol de un usuario de oficina (ADMIN, GESTOR, CONSULTA)")
    public ResponseEntity<UsuarioOficinaDTO> cambiarRol(
            @PathVariable Long id,
            @Valid @RequestBody CambiarRolRequest request,
            Authentication authentication) {
        Rol nuevoRol;
        try {
            nuevoRol = Rol.valueOf(request.rol().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Rol no válido: " + request.rol()
                    + ". Valores admitidos: ADMIN, GESTOR, CONSULTA.");
        }
        return ResponseEntity.ok(usuarioOficinaService.cambiarRol(
                id, nuevoRol, authentication.getName()));
    }

    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "Eliminar usuario de oficina",
            description = "Por defecto, borrado lógico (se conserva para auditoría). Con real=true, borrado físico si no tiene historial asociado")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean real) {
        usuarioOficinaService.eliminar(id, real);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitaciones")
    @Operation(summary = "Invitar a un nuevo administrador por email",
            description = "Crea el usuario inactivo y envia un enlace de invitacion por email. Solo ADMIN.")
    public ResponseEntity<InvitacionPendienteDTO> invitar(
            @Valid @RequestBody InvitarUsuarioRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(usuarioOficinaService.invitar(request, authentication.getName()));
    }

    @GetMapping("/invitaciones/pendientes")
    @Operation(summary = "Listar invitaciones pendientes de aceptacion")
    public ResponseEntity<List<InvitacionPendienteDTO>> findInvitacionesPendientes() {
        return ResponseEntity.ok(usuarioOficinaService.findInvitacionesPendientes());
    }

    @PostMapping("/invitaciones/{id}/reenviar")
    @Operation(summary = "Reenviar una invitacion con un nuevo enlace")
    public ResponseEntity<InvitacionPendienteDTO> reenviarInvitacion(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioOficinaService.reenviarInvitacion(id));
    }

    @DeleteMapping("/invitaciones/{id}")
    @Operation(summary = "Cancelar una invitacion pendiente")
    public ResponseEntity<Void> cancelarInvitacion(@PathVariable Long id) {
        usuarioOficinaService.cancelarInvitacion(id);
        return ResponseEntity.noContent().build();
    }
}
