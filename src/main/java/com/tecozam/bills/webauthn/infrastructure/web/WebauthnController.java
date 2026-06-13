package com.tecozam.bills.webauthn.infrastructure.web;

import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.webauthn.application.WebauthnAuthenticationService;
import com.tecozam.bills.webauthn.application.WebauthnRegistrationService;
import com.tecozam.bills.webauthn.domain.WebauthnCredential;
import com.tecozam.bills.webauthn.dto.AssertionFinishRequest;
import com.tecozam.bills.webauthn.dto.AssertionStartResponse;
import com.tecozam.bills.webauthn.dto.CredentialSummary;
import com.tecozam.bills.webauthn.dto.RegisterFinishRequest;
import com.tecozam.bills.webauthn.dto.RegisterStartResponse;
import com.tecozam.bills.webauthn.infrastructure.persistence.WebauthnCredentialRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Endpoints WebAuthn / FIDO2 — solo accesibles para usuarios CAMPO.
 *
 * Flujo de enrolamiento:
 *   POST /api/webauthn/register/options → token + opciones para navigator.credentials.create()
 *   POST /api/webauthn/register/verify  → persiste la credencial
 *
 * Flujo de assertion (usado por /pin/reveal en Phase 3):
 *   POST /api/webauthn/auth/options → token + opciones para navigator.credentials.get()
 *   POST /api/webauthn/auth/verify  → devuelve OK con el id del usuario verificado
 *
 * Gestión:
 *   GET    /api/webauthn/credentials       → lista las credenciales del propio usuario
 *   DELETE /api/webauthn/credentials/{id}  → borrado lógico (eliminado_en = now)
 */
@RestController
@RequestMapping("/api/webauthn")
@RequiredArgsConstructor
@Tag(name = "WebAuthn", description = "Enrolamiento y verificación biométrica FIDO2")
@PreAuthorize("hasRole('CAMPO')")
public class WebauthnController {

    private final WebauthnRegistrationService registrationService;
    private final WebauthnAuthenticationService authenticationService;
    private final WebauthnCredentialRepository credRepo;
    private final UsuarioCampoRepository userRepo;

    @PostMapping("/register/options")
    public ResponseEntity<RegisterStartResponse> startRegistration(Authentication auth) {
        return ResponseEntity.ok(registrationService.startRegistration(auth.getName()));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<Map<String, Object>> finishRegistration(
            @Valid @RequestBody RegisterFinishRequest body,
            Authentication auth) {
        WebauthnCredential cred = registrationService.finishRegistration(
                body.token(), body.credentialJson(), body.deviceName());
        return ResponseEntity.ok(Map.of("success", true, "credentialId", cred.getId()));
    }

    @PostMapping("/auth/options")
    public ResponseEntity<AssertionStartResponse> startAssertion(Authentication auth) {
        return ResponseEntity.ok(authenticationService.startAssertion(auth.getName()));
    }

    @PostMapping("/auth/verify")
    public ResponseEntity<Map<String, Object>> finishAssertion(
            @Valid @RequestBody AssertionFinishRequest body) {
        Long usuarioId = authenticationService.verifyAssertion(body.token(), body.credentialJson());
        return ResponseEntity.ok(Map.of("success", true, "usuarioId", usuarioId));
    }

    @GetMapping("/credentials")
    public ResponseEntity<List<CredentialSummary>> listCredentials(Authentication auth) {
        UsuarioCampo u = userRepo.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("UsuarioCampo", auth.getName()));
        return ResponseEntity.ok(credRepo.findActiveByUsuarioCampoId(u.getId())
                .map(c -> List.of(new CredentialSummary(
                        c.getId(),
                        c.getNombreDispositivo(),
                        c.getCreadoEn(),
                        c.getSignatureCount())))
                .orElse(List.of()));
    }

    @DeleteMapping("/credentials/{id}")
    public ResponseEntity<Void> deleteCredential(@PathVariable Long id, Authentication auth) {
        UsuarioCampo u = userRepo.findByUsername(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("UsuarioCampo", auth.getName()));

        WebauthnCredential cred = credRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WebauthnCredential", id));

        if (cred.getUsuarioCampoId() == null || !cred.getUsuarioCampoId().equals(u.getId())) {
            throw new BusinessException("La credencial no pertenece al usuario autenticado");
        }

        cred.setEliminadoEn(LocalDateTime.now());
        credRepo.save(cred);
        return ResponseEntity.noContent().build();
    }
}
