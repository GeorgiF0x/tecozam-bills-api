package com.tecozam.bills.factura.domain;

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
@Table(name = "operaciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Operacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarjeta_resumen_id", nullable = false)
    private TarjetaResumen tarjetaResumen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_id", nullable = false)
    private Factura factura;

    @Column(name = "referencia", length = 500)
    private String referencia;

    @Column(name = "concepto_original", length = 500)
    private String conceptoOriginal;

    @Column(name = "concepto_unificado", length = 500)
    private String conceptoUnificado;

    @Column(name = "establecimiento", length = 500)
    private String establecimiento;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @Column(name = "fecha_hora")
    private LocalDateTime fechaHora;

    @Column(name = "kms")
    private Integer kms;

    @Column(name = "cantidad", precision = 10, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "precio_neto", precision = 8, scale = 3)
    private BigDecimal precioNeto;

    @Column(name = "precio_iva_inc", precision = 8, scale = 3)
    private BigDecimal precioIvaInc;

    @Column(name = "precio_unitario", precision = 8, scale = 3)
    private BigDecimal precioUnitario;

    @Column(name = "precio_aplicado", precision = 8, scale = 3)
    private BigDecimal precioAplicado;

    @Column(name = "importe", precision = 10, scale = 2)
    private BigDecimal importe;

    @Column(name = "importe_total", precision = 10, scale = 2)
    private BigDecimal importeTotal;

    @Column(name = "dto_porcentaje", precision = 5, scale = 2)
    private BigDecimal dtoPorcentaje;

    @Column(name = "dto_total", precision = 10, scale = 2)
    private BigDecimal dtoTotal;

    @Column(name = "bonificacion", precision = 10, scale = 2)
    private BigDecimal bonificacion;

    @PrePersist
    @PreUpdate
    private void truncateFields() {
        referencia = truncate(referencia, 500);
        conceptoOriginal = truncate(conceptoOriginal, 500);
        conceptoUnificado = truncate(conceptoUnificado, 500);
        establecimiento = truncate(establecimiento, 500);
        observaciones = truncate(observaciones, 500);
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
