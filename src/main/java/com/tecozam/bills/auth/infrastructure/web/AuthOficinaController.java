package com.tecozam.bills.auth.infrastructure.web;

import com.tecozam.bills.auth.application.AuthOficinaService;
import com.tecozam.bills.auth.dto.LoginRequest;
import com.tecozam.bills.auth.dto.TokenResponse;
import com.tecozam.bills.auth.dto.UsuarioOficinaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/oficina")
@RequiredArgsConstructor
@Tag(name = "Auth Oficina", description = "Autenticación para usuarios de oficina (ADMIN, GESTOR, ENCARGADO)")
public class AuthOficinaController {

    private final AuthOficinaService authOficinaService;

    @PostMapping("/login")
    @Operation(summary = "Login oficina", description = "Autentica un usuario de oficina y retorna tokens JWT")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authOficinaService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar token oficina")
    public ResponseEntity<TokenResponse> refresh(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authOficinaService.refresh(body.get("refreshToken")));
    }

    @GetMapping("/me")
    @Operation(summary = "Perfil usuario oficina", description = "Devuelve el perfil del usuario de oficina autenticado")
    public ResponseEntity<UsuarioOficinaDTO> me(Authentication authentication) {
        return ResponseEntity.ok(authOficinaService.me(authentication.getName()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout oficina")
    public ResponseEntity<Void> logout(Authentication authentication) {
        authOficinaService.logout(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
