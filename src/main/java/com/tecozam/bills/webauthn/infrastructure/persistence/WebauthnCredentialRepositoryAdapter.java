package com.tecozam.bills.webauthn.infrastructure.persistence;

import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.webauthn.domain.WebauthnCredential;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;

/**
 * Adapter que expone los repositorios JPA del proyecto como el
 * {@link CredentialRepository} de yubico (yubico es agnóstico al storage).
 *
 * Convenciones:
 * <ul>
 *   <li>{@code userHandle} = id de {@link UsuarioCampo} serializado como big-endian
 *       en 8 bytes ({@link ByteBuffer#putLong}). Es estable y único por usuario.</li>
 *   <li>v1: 1 credencial activa por usuario (UNIQUE filtered en BD), así que
 *       {@link #getCredentialIdsForUsername(String)} devuelve 0 o 1 descriptor.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class WebauthnCredentialRepositoryAdapter implements CredentialRepository {

    private final WebauthnCredentialRepository credRepo;
    private final UsuarioCampoRepository userRepo;

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return userRepo.findByUsername(username)
                .map(UsuarioCampo::getId)
                .flatMap(credRepo::findActiveByUsuarioCampoId)
                .map(c -> Set.of(PublicKeyCredentialDescriptor.builder()
                        .id(new ByteArray(c.getCredentialId()))
                        .build()))
                .orElse(Set.of());
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return userRepo.findByUsername(username)
                .map(u -> userIdToByteArray(u.getId()));
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        try {
            long id = byteArrayToUserId(userHandle);
            return userRepo.findById(id).map(UsuarioCampo::getUsername);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        // El userHandle es OPCIONAL en la assertion: cuando la credencial es
        // non-discoverable (que es nuestro caso, no marcamos residentKey), Chrome
        // y Safari pueden enviar userHandle vacio. Si exigieramos 8 bytes con
        // byteArrayToUserId, peta con "UserHandle no es un id long valido" y la
        // assertion completa muere.
        // El credentialId ya es unico globalmente y la firma se valida con la
        // clave publica guardada — no hace falta cross-check por userId.
        return credRepo.findActiveByCredentialId(credentialId.getBytes())
                .filter(c -> {
                    if (userHandle == null || userHandle.getBytes().length != Long.BYTES) {
                        return true;
                    }
                    long userId = ByteBuffer.wrap(userHandle.getBytes()).getLong();
                    return c.getUsuarioCampoId() != null && c.getUsuarioCampoId() == userId;
                })
                .map(this::toRegistered);
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return credRepo.findActiveByCredentialId(credentialId.getBytes())
                .map(c -> Set.of(toRegistered(c)))
                .orElse(Set.of());
    }

    private RegisteredCredential toRegistered(WebauthnCredential c) {
        return RegisteredCredential.builder()
                .credentialId(new ByteArray(c.getCredentialId()))
                .userHandle(userIdToByteArray(c.getUsuarioCampoId()))
                .publicKeyCose(new ByteArray(c.getPublicKey()))
                .signatureCount(c.getSignatureCount())
                .build();
    }

    public static ByteArray userIdToByteArray(Long id) {
        return new ByteArray(ByteBuffer.allocate(Long.BYTES).putLong(id).array());
    }

    public static long byteArrayToUserId(ByteArray handle) {
        if (handle.getBytes().length != Long.BYTES) {
            throw new IllegalArgumentException("UserHandle no es un id long válido");
        }
        return ByteBuffer.wrap(handle.getBytes()).getLong();
    }
}
