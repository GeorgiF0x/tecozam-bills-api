package com.tecozam.bills.webauthn.domain;

import com.tecozam.bills.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Credencial FIDO2 / WebAuthn registrada por un usuario CAMPO.
 * v1: una credencial activa por usuario (UNIQUE filtered por eliminado_en).
 */
@Entity
@Table(name = "webauthn_credentials")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class WebauthnCredential extends BaseEntity {

    @Column(name = "usuario_campo_id", nullable = false)
    private Long usuarioCampoId;

    @Column(name = "credential_id", nullable = false, length = 255)
    private byte[] credentialId;

    @Lob
    @Column(name = "public_key", nullable = false)
    private byte[] publicKey;

    @Column(name = "signature_count", nullable = false)
    @Builder.Default
    private long signatureCount = 0;

    @Column(columnDefinition = "binary(16)")
    private byte[] aaguid;

    @Column(name = "nombre_dispositivo", length = 100)
    private String nombreDispositivo;
}
