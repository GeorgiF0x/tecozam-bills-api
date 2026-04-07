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
@Table(name = "factura_concepto_resumen")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacturaConceptoResumen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_id", nullable = false)
    private Factura factura;

    @Column(name = "concepto_original")
    private String conceptoOriginal;

    @Column(name = "concepto_unificado")
    private String conceptoUnificado;

    @Column(name = "cantidad", precision = 12, scale = 2)
    private BigDecimal cantidad;

    @Column(name = "base_imponible", precision = 12, scale = 2)
    private BigDecimal baseImponible;

    @Column(name = "tipo_iva", precision = 5, scale = 2)
    private BigDecimal tipoIva;

    @Column(name = "cuota_iva", precision = 12, scale = 2)
    private BigDecimal cuotaIva;

    @Column(name = "importe", precision = 12, scale = 2)
    private BigDecimal importe;
}
