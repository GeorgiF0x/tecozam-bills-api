package com.tecozam.bills.auth.infrastructure.web;

import com.tecozam.bills.auth.application.AuthCampoService;
import com.tecozam.bills.auth.dto.LoginRequest;
import com.tecozam.bills.auth.dto.TokenResponse;
import com.tecozam.bills.auth.dto.UsuarioCampoDTO;
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
@RequestMapping("/api/auth/campo")
@RequiredArgsConstructor
@Tag(name = "Auth Campo", description = "Autenticación para usuarios de campo (conductores/operarios)")
public class AuthCampoController {

    private final AuthCampoService authCampoService;

    @PostMapping("/login")
    @Operation(summary = "Login campo", description = "Autentica un usuario de campo y retorna tokens JWT")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authCampoService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renovar token campo")
    public ResponseEntity<TokenResponse> refresh(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authCampoService.refresh(body.get("refreshToken")));
    }

    @GetMapping("/me")
    @Operation(summary = "Perfil usuario campo", description = "Devuelve el perfil del usuario de campo autenticado")
    public ResponseEntity<UsuarioCampoDTO> me(Authentication authentication) {
        return ResponseEntity.ok(authCampoService.me(authentication.getName()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout campo")
    public ResponseEntity<Void> logout(Authentication authentication) {
        authCampoService.logout(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
