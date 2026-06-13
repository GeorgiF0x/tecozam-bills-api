package com.tecozam.bills.webauthn.application;

import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.webauthn.dto.AssertionStartResponse;
import com.tecozam.bills.webauthn.infrastructure.persistence.WebauthnCredentialRepository;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebauthnAuthenticationServiceTest {

    @Mock RelyingParty relyingParty;
    @Mock WebauthnCredentialRepository credRepo;
    @Mock UsuarioCampoRepository userRepo;
    ChallengeStore challengeStore = new ChallengeStore();

    WebauthnAuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new WebauthnAuthenticationService(relyingParty, credRepo, userRepo, challengeStore);
    }

    @Test
    @DisplayName("startAssertion devuelve token + options serializadas y guarda challenge")
    void startAssertion_devuelveOptions() throws Exception {
        UsuarioCampo u = new UsuarioCampo();
        u.setId(42L);
        u.setUsername("campo");
        when(userRepo.findByUsername("campo")).thenReturn(Optional.of(u));

        AssertionRequest req = mock(AssertionRequest.class);
        when(req.toCredentialsGetJson()).thenReturn("{\"publicKey\":\"...\"}");
        when(relyingParty.startAssertion(any(StartAssertionOptions.class))).thenReturn(req);

        AssertionStartResponse resp = service.startAssertion("campo");

        assertThat(resp.token()).isNotBlank();
        assertThat(resp.publicKeyCredentialRequestOptions()).contains("publicKey");
        assertThat(challengeStore.consume(resp.token())).isPresent();
    }

    @Test
    @DisplayName("startAssertion falla si el usuario no existe")
    void startAssertion_usuarioInexistente() {
        when(userRepo.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startAssertion("fantasma"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("verifyAssertion con token consumido rechaza el replay")
    void verifyAssertion_replayRechazado() {
        // Sin nada en el store: simula un replay sobre un token ya consumido.
        assertThatThrownBy(() -> service.verifyAssertion("ghost", "{}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("challenge");
    }
}
