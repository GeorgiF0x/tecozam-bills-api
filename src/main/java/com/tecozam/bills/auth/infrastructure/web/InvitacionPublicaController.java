package com.tecozam.bills.auth.infrastructure.web;

import com.tecozam.bills.auth.application.UsuarioOficinaService;
import com.tecozam.bills.auth.dto.AceptarInvitacionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitaciones")
@RequiredArgsConstructor
@Tag(name = "Invitaciones", description = "Aceptacion publica de invitaciones de administrador")
public class InvitacionPublicaController {

    private final UsuarioOficinaService usuarioOficinaService;

    @PostMapping("/{token}/aceptar")
    @Operation(summary = "Aceptar una invitacion de administrador",
            description = "Endpoint publico: activa la cuenta con la password elegida.")
    public ResponseEntity<Void> aceptar(
            @PathVariable String token,
            @Valid @RequestBody AceptarInvitacionRequest request) {
        usuarioOficinaService.aceptarInvitacion(token, request);
        return ResponseEntity.noContent().build();
    }
}
