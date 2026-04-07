package com.tecozam.bills.factura.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "tarjeta_resumen_conceptos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TarjetaResumenConcepto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarjeta_resumen_id", nullable = false)
    private TarjetaResumen tarjetaResumen;

    @Column(name = "concepto_original")
    private String conceptoOriginal;

    @Column(name = "concepto_unificado")
    private String conceptoUnificado;

    @Column(name = "importe", precision = 10, scale = 2)
    private BigDecimal importe;

    @Column(name = "cantidad", precision = 10, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "total_bonificacion", precision = 10, scale = 2)
    private BigDecimal totalBonificacion;
}
