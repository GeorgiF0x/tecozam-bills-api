package com.tecozam.bills.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tabla", nullable = false, length = 100)
    private String tabla;

    @Column(name = "registro_id")
    private Long registroId;

    @Column(name = "accion", nullable = false, length = 10)
    private String accion;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "datos_anteriores", columnDefinition = "nvarchar(max)")
    private String datosAnteriores;

    @Column(name = "datos_nuevos", columnDefinition = "nvarchar(max)")
    private String datosNuevos;

    @Column(name = "fecha", nullable = false, updatable = false)
    private LocalDateTime fecha;

    @PrePersist
    protected void onPrePersist() {
        if (this.fecha == null) {
            this.fecha = LocalDateTime.now();
        }
    }
}
