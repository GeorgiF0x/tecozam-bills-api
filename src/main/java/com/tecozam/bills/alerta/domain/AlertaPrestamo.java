package com.tecozam.bills.alerta.domain;

import com.tecozam.bills.prestamo.domain.Prestamo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "alertas_prestamo")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertaPrestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prestamo_id", nullable = false)
    private Prestamo prestamo;

    @Column(name = "tipo_alerta", nullable = false)
    private String tipoAlerta;

    @Column(name = "fecha_alerta", nullable = false)
    private LocalDate fechaAlerta;

    @Column(name = "mensaje", length = 500)
    private String mensaje;

    @Column(name = "email_enviado", nullable = false)
    @Builder.Default
    private boolean emailEnviado = false;

    @Column(name = "leida", nullable = false)
    @Builder.Default
    private boolean leida = false;

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onPrePersist() {
        this.creadoEn = LocalDateTime.now();
    }
}
