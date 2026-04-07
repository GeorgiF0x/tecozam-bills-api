package com.tecozam.bills.ticket.domain;

import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String origen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trabajador_id")
    private Trabajador trabajador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarjeta_id")
    private Tarjeta tarjeta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehiculo_id")
    private Vehiculo vehiculo;

    @Column(name = "estacion")
    private String estacion;

    @Column(name = "direccion")
    private String direccion;

    @Column(name = "num_tarjeta_4ultimos")
    private String numTarjeta4ultimos;

    @Column(name = "matricula")
    private String matricula;

    @Column(name = "producto")
    private String producto;

    @Column(name = "num_recibo")
    private String numRecibo;

    @Column(name = "nif_estacion")
    private String nifEstacion;

    @Column(name = "imagen_url")
    private String imagenUrl;

    @Column(name = "concepto")
    private String concepto;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "fecha_hora")
    private LocalDateTime fechaHora;

    @Column(name = "kms")
    private Integer kms;

    @Column(name = "litros", precision = 10, scale = 2)
    private BigDecimal litros;

    @Column(name = "precio_litro", precision = 8, scale = 3)
    private BigDecimal precioLitro;

    @Column(name = "importe_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal importeTotal;

    @Column(name = "estado_cotejo", nullable = false)
    @Builder.Default
    private String estadoCotejo = "PENDIENTE";

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "operacion_cotejada_id")
    private Operacion operacionCotejada;

    @Column(name = "tipo_incidencia", length = 50)
    private String tipoIncidencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignado_a_id")
    private com.tecozam.bills.auth.domain.Usuario asignadoA;

    @Column(name = "notas_resolucion", length = 500)
    private String notasResolucion;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

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
