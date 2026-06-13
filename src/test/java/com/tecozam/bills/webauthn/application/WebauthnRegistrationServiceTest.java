package com.tecozam.bills.webauthn.application;

import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.webauthn.domain.WebauthnCredential;
import com.tecozam.bills.webauthn.dto.RegisterStartResponse;
import com.tecozam.bills.webauthn.infrastructure.persistence.WebauthnCredentialRepository;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebauthnRegistrationServiceTest {

    @Mock RelyingParty relyingParty;
    @Mock WebauthnCredentialRepository credRepo;
    @Mock UsuarioCampoRepository userRepo;
    ChallengeStore challengeStore = new ChallengeStore();

    WebauthnRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new WebauthnRegistrationService(relyingParty, credRepo, userRepo, challengeStore);
    }

    @Test
    @DisplayName("startRegistration de usuario sin credencial devuelve token + options y guarda challenge")
    void startRegistration_nuevoUsuario() throws Exception {
        UsuarioCampo u = new UsuarioCampo();
        u.setId(42L);
        u.setUsername("campo");
        when(userRepo.findByUsername("campo")).thenReturn(Optional.of(u));
        when(credRepo.findActiveByUsuarioCampoId(42L)).thenReturn(Optional.empty());

        PublicKeyCredentialCreationOptions options = mock(PublicKeyCredentialCreationOptions.class);
        when(options.toCredentialsCreateJson()).thenReturn("{\"publicKey\":\"...\"}");
        when(relyingParty.startRegistration(any(StartRegistrationOptions.class))).thenReturn(options);

        RegisterStartResponse resp = service.startRegistration("campo");

        assertThat(resp.token()).isNotBlank();
        assertThat(resp.publicKeyCredentialCreationOptions()).contains("publicKey");
        assertThat(challengeStore.consume(resp.token())).isPresent();
    }

    @Test
    @DisplayName("startRegistration falla si el usuario ya tiene credencial activa")
    void startRegistration_yaEnrolado() {
        UsuarioCampo u = new UsuarioCampo();
        u.setId(42L);
        u.setUsername("campo");
        when(userRepo.findByUsername("campo")).thenReturn(Optional.of(u));

        WebauthnCredential existing = WebauthnCredential.builder().usuarioCampoId(42L).build();
        when(credRepo.findActiveByUsuarioCampoId(42L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.startRegistration("campo"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya tiene una credencial");
    }

    @Test
    @DisplayName("startRegistration falla si el usuario no existe")
    void startRegistration_usuarioInexistente() {
        when(userRepo.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startRegistration("fantasma"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("finishRegistration con token desconocido lanza BusinessException")
    void finishRegistration_tokenInvalido() {
        assertThatThrownBy(() -> service.finishRegistration("ghost-token", "{}", "Mi móvil"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("challenge");
    }
}
