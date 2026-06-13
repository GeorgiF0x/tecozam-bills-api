package com.tecozam.bills.webauthn.application;

import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.webauthn.domain.WebauthnCredential;
import com.tecozam.bills.webauthn.dto.AssertionStartResponse;
import com.tecozam.bills.webauthn.infrastructure.persistence.WebauthnCredentialRepository;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.data.AuthenticatorAssertionResponse;
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.exception.AssertionFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta la verificación WebAuthn / FIDO2 (assertion) de un usuario CAMPO ya
 * enrolado. Empareja con {@link WebauthnRegistrationService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebauthnAuthenticationService {

    private final RelyingParty relyingParty;
    private final WebauthnCredentialRepository credRepo;
    private final UsuarioCampoRepository userRepo;
    private final ChallengeStore challengeStore;

    public AssertionStartResponse startAssertion(String username) {
        UsuarioCampo u = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("UsuarioCampo", username));

        AssertionRequest req = relyingParty.startAssertion(
                StartAssertionOptions.builder()
                        .username(u.getUsername())
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build());

        // OJO: yubico expone dos formatos JSON distintos para AssertionRequest.
        // - toCredentialsGetJson(): {"publicKey": {...}} — el formato que entiende
        //   navigator.credentials.get() en el browser. Va al cliente.
        // - toJson(): {"publicKeyCredentialRequestOptions": {...}, "username": "..."}
        //   — el formato que entiende AssertionRequest.fromJson(...). Es el que
        //   guardamos para reconstruir el request en verifyAssertion.
        // Antes guardabamos toCredentialsGetJson y al deserializarlo el campo
        // publicKeyCredentialRequestOptions venia null -> BusinessException.
        String wireJson;
        String storedJson;
        try {
            wireJson = req.toCredentialsGetJson();
            storedJson = req.toJson();
        } catch (Exception ex) {
            throw new BusinessException("No se pudieron serializar las opciones de assertion: " + ex.getMessage());
        }
        String token = UUID.randomUUID().toString();
        challengeStore.put(token, storedJson.getBytes(), u.getId());

        return new AssertionStartResponse(token, wireJson);
    }

    /**
     * Verifica una assertion para el token dado. Devuelve el id del UsuarioCampo
     * asociado al challenge si la verificación es válida. Marca single-use,
     * actualiza signatureCount.
     */
    @Transactional
    public Long verifyAssertion(String token, String credentialJson) {
        Optional<ChallengeStore.Entry> entryOpt = challengeStore.consume(token);
        if (entryOpt.isEmpty()) {
            throw new BusinessException("Token de challenge inválido o expirado");
        }
        ChallengeStore.Entry entry = entryOpt.get();

        try {
            AssertionRequest req = AssertionRequest.fromJson(new String(entry.payload()));

            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc =
                    PublicKeyCredential.parseAssertionResponseJson(credentialJson);

            AssertionResult result = relyingParty.finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(req)
                            .response(pkc)
                            .build());

            if (!result.isSuccess()) {
                throw new BusinessException("Assertion WebAuthn rechazada");
            }

            // Actualizar signature counter para evitar clone attacks
            WebauthnCredential cred = credRepo.findActiveByCredentialId(result.getCredentialId().getBytes())
                    .orElseThrow(() -> new BusinessException("Credencial no encontrada tras verificación"));
            cred.setSignatureCount(result.getSignatureCount());
            credRepo.save(cred);

            log.info("WebAuthn assertion OK usuario={} signatureCount={}",
                    entry.usuarioCampoId(), result.getSignatureCount());
            return entry.usuarioCampoId();
        } catch (AssertionFailedException ex) {
            throw new BusinessException("Assertion WebAuthn rechazada: " + ex.getMessage());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("No se pudo procesar la assertion: " + ex.getMessage());
        }
    }
}
