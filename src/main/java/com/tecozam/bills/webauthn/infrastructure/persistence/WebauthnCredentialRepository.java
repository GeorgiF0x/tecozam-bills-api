package com.tecozam.bills.webauthn.infrastructure.persistence;

import com.tecozam.bills.webauthn.domain.WebauthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WebauthnCredentialRepository extends JpaRepository<WebauthnCredential, Long> {

    @Query("SELECT c FROM WebauthnCredential c WHERE c.usuarioCampoId = :usuarioCampoId AND c.eliminadoEn IS NULL")
    Optional<WebauthnCredential> findActiveByUsuarioCampoId(@Param("usuarioCampoId") Long usuarioCampoId);

    @Query("SELECT c FROM WebauthnCredential c WHERE c.credentialId = :credentialId AND c.eliminadoEn IS NULL")
    Optional<WebauthnCredential> findActiveByCredentialId(@Param("credentialId") byte[] credentialId);
}
