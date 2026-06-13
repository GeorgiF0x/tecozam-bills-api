package com.tecozam.bills.auditoria.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Registro de auditoría de cada acceso al PIN de tarjeta — guardado, revelado
 * con biometría o revelado con password. Sin soft-delete: los eventos son
 * inmutables por definición.
 */
@Entity
@Table(name = "auditoria_pin_acceso")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PinAccesoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_campo_id", nullable = false)
    private Long usuarioCampoId;

    @Column(name = "tarjeta_id", nullable = false)
    private Long tarjetaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetodoPinAcceso metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResultadoPinAcceso resultado;

    @Column(nullable = false, length = 45)
    private String ip;

    @Column(name = "user_agent", nullable = false, length = 500)
    private String userAgent;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;
}
