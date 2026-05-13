package com.tecozam.bills.prestamo.domain;

import com.tecozam.bills.centrocoste.domain.CentroCoste;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import com.tecozam.bills.viat.domain.Viat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "prestamos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_recurso", nullable = false)
    private String tipoRecurso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarjeta_id")
    private Tarjeta tarjeta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viat_id")
    private Viat viat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_id")
    private Vehiculo vehiculo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trabajador_id", nullable = false)
    private Trabajador trabajador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "centro_coste_id", nullable = false)
    private CentroCoste centroCoste;

    @Column(name = "tipo_prestamo")
    private String tipoPrestamo;

    @Column(name = "estado", nullable = false)
    @Builder.Default
    private String estado = "ACTIVO";

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin_prevista")
    private LocalDate fechaFinPrevista;

    @Column(name = "fecha_devolucion_real")
    private LocalDate fechaDevolucionReal;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    /** True si el préstamo lo creó el propio operario desde la PWA (self-service). */
    @Column(name = "creado_por_campo", nullable = false)
    @Builder.Default
    private boolean creadoPorCampo = false;

    @Column(name = "alerta_3d_enviada", nullable = false)
    @Builder.Default
    private boolean alerta3dEnviada = false;

    @Column(name = "alerta_1d_enviada", nullable = false)
    @Builder.Default
    private boolean alerta1dEnviada = false;

    @Column(name = "alerta_hoy_enviada", nullable = false)
    @Builder.Default
    private boolean alertaHoyEnviada = false;

    @Column(name = "email_ultimo_dia", nullable = false)
    @Builder.Default
    private boolean emailUltimoDia = false;

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "modificado_en")
    private LocalDateTime modificadoEn;

    @PrePersist
    protected void onPrePersist() {
        this.creadoEn = LocalDateTime.now();
        this.modificadoEn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onPreUpdate() {
        this.modificadoEn = LocalDateTime.now();
    }
}
