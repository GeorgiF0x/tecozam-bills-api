package com.tecozam.bills.webauthn.application;

import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.webauthn.domain.WebauthnCredential;
import com.tecozam.bills.webauthn.dto.RegisterStartResponse;
import com.tecozam.bills.webauthn.infrastructure.persistence.WebauthnCredentialRepository;
import com.tecozam.bills.webauthn.infrastructure.persistence.WebauthnCredentialRepositoryAdapter;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorAttachment;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.exception.RegistrationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Orquesta el enrolamiento WebAuthn / FIDO2 de un usuario CAMPO.
 *
 * Flujo:
 *   1. {@link #startRegistration(String)} guarda en {@link ChallengeStore} las
 *      opciones serializadas y devuelve un token + JSON al cliente.
 *   2. La PWA llama a navigator.credentials.create() y postea el resultado.
 *   3. {@link #finishRegistration(String, String, String)} valida el attestation
 *      contra las opciones cacheadas y persiste la {@link WebauthnCredential}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebauthnRegistrationService {

    private final RelyingParty relyingParty;
    private final WebauthnCredentialRepository credRepo;
    private final UsuarioCampoRepository userRepo;
    private final ChallengeStore challengeStore;

    @Transactional
    public RegisterStartResponse startRegistration(String username) {
        UsuarioCampo u = userRepo.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("UsuarioCampo", username));

        // Re-enrolment: soft-deleteamos la credencial activa anterior AQUI, no en
        // finishRegistration. Razon: yubico llama internamente a
        // getCredentialIdsForUsername para construir excludeCredentials en las
        // opciones que enviamos al browser. Si la vieja sigue activa, iOS detecta
        // que su Keychain ya tiene esa passkey y aborta con InvalidStateError
        // antes incluso de pedir Face ID. Soft-deletear antes de generar las
        // opciones hace que excludeCredentials vaya vacio y iOS deje crear.
        // Trade-off: si el usuario abandona el flow sin completar la attestation,
        // queda sin biometria hasta que vuelva a enrolar — el fallback de
        // contrasena sigue disponible, asi que es aceptable.
        credRepo.findActiveByUsuarioCampoId(u.getId())
                .ifPresent(old -> {
                    old.softDelete();
                    credRepo.save(old);
                    log.info("WebAuthn: credencial anterior desactivada para re-enrol usuario={} credentialDbId={}",
                            u.getId(), old.getId());
                });

        UserIdentity userIdentity = UserIdentity.builder()
                .name(u.getUsername())
                .displayName(safeDisplayName(u))
                .id(WebauthnCredentialRepositoryAdapter.userIdToByteArray(u.getId()))
                .build();

        // Forzamos authenticator de plataforma (huella del móvil / FaceID / Windows Hello)
        // y user verification REQUIRED → Chrome NO ofrece QR cross-device ni llave USB.
        // Si el dispositivo no tiene biometría, navigator.credentials.create() falla y
        // el operario usa el fallback de contraseña.
        AuthenticatorSelectionCriteria selection = AuthenticatorSelectionCriteria.builder()
                .authenticatorAttachment(AuthenticatorAttachment.PLATFORM)
                .userVerification(UserVerificationRequirement.REQUIRED)
                .build();

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder()
                        .user(userIdentity)
                        .authenticatorSelection(selection)
                        .build());

        // Mismo dual-format que AssertionRequest (ver WebauthnAuthenticationService).
        // - toCredentialsCreateJson(): {"publicKey": {...}} -> navigator.credentials.create()
        // - toJson(): {...} sin wrapper -> PublicKeyCredentialCreationOptions.fromJson()
        // Antes guardabamos el formato browser y al deserializarlo se rompia el
        // parsing en finishRegistration. En registration era menos visible que en
        // assertion pero el patron es el mismo.
        String wireJson;
        String storedJson;
        try {
            wireJson = options.toCredentialsCreateJson();
            storedJson = options.toJson();
        } catch (Exception ex) {
            throw new BusinessException("No se pudieron serializar las opciones WebAuthn: " + ex.getMessage());
        }
        String token = UUID.randomUUID().toString();
        challengeStore.put(token, storedJson.getBytes(), u.getId());

        return new RegisterStartResponse(token, wireJson);
    }

    @Transactional
    public WebauthnCredential finishRegistration(String token, String credentialJson, String deviceName) {
        Optional<ChallengeStore.Entry> entryOpt = challengeStore.consume(token);
        if (entryOpt.isEmpty()) {
            throw new BusinessException("Token de challenge inválido o expirado");
        }
        ChallengeStore.Entry entry = entryOpt.get();

        try {
            PublicKeyCredentialCreationOptions options =
                    PublicKeyCredentialCreationOptions.fromJson(new String(entry.payload()));

            @SuppressWarnings({"rawtypes", "unchecked"})
            PublicKeyCredential pkc = PublicKeyCredential.parseRegistrationResponseJson(credentialJson);

            RegistrationResult result = relyingParty.finishRegistration(
                    FinishRegistrationOptions.builder()
                            .request(options)
                            .response(pkc)
                            .build());

            // La credencial anterior (si la habia) ya fue soft-deleted en
            // startRegistration — necesario para que excludeCredentials vaya
            // vacio y iOS deje crear la nueva. Aqui solo persistimos la nueva.
            WebauthnCredential cred = WebauthnCredential.builder()
                    .usuarioCampoId(entry.usuarioCampoId())
                    .credentialId(result.getKeyId().getId().getBytes())
                    .publicKey(result.getPublicKeyCose().getBytes())
                    .signatureCount(result.getSignatureCount())
                    .aaguid(result.getAaguid().getBytes())
                    .nombreDispositivo(deviceName)
                    .build();

            credRepo.save(cred);
            log.info("WebAuthn: credencial registrada usuario={} deviceName={}",
                    entry.usuarioCampoId(), deviceName);
            return cred;
        } catch (RegistrationFailedException ex) {
            throw new BusinessException("Attestation inválida: " + ex.getMessage());
        } catch (Exception ex) {
            throw new BusinessException("No se pudo procesar el registro WebAuthn: " + ex.getMessage());
        }
    }

    private static String safeDisplayName(UsuarioCampo u) {
        StringBuilder sb = new StringBuilder();
        if (u.getNombre() != null) sb.append(u.getNombre());
        if (u.getApellidos() != null) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(u.getApellidos());
        }
        return sb.length() > 0 ? sb.toString() : u.getUsername();
    }
}
