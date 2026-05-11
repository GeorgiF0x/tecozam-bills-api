package com.tecozam.bills.tarjeta.domain;

import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.shared.domain.BaseEntity;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.persistence.AesEncryptorConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tarjetas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class Tarjeta extends BaseEntity {

    @Column(name = "numero_tarjeta", nullable = false, unique = true, length = 50)
    private String numeroTarjeta;

    @Column(length = 100)
    private String alias;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoRecurso estado = EstadoRecurso.DISPONIBLE;

    @Column(nullable = false)
    @Builder.Default
    private boolean activa = true;

    /**
     * PIN de la tarjeta (4 dígitos), encriptado con AES-256-GCM.
     * Lo guarda el conductor desde la app móvil y se valida al enviar tickets OCR.
     * El valor en BD es base64 con formato [IV][ciphertext][tag].
     */
    @Convert(converter = AesEncryptorConverter.class)
    @Column(name = "pin_encrypted", length = 200)
    private String pinEncrypted;
}
