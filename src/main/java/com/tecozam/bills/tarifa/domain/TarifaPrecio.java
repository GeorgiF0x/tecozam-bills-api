package com.tecozam.bills.tarifa.domain;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "tarifa_precios")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class TarifaPrecio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarifa_id", nullable = false)
    private Tarifa tarifa;

    @Column(name = "producto", nullable = false, length = 100)
    private String producto;

    @Column(name = "concepto_unificado", length = 50)
    private String conceptoUnificado;

    @Column(name = "precio_sin_iva", nullable = false, precision = 8, scale = 3)
    private BigDecimal precioSinIva;

    @Column(name = "precio_con_iva", nullable = false, precision = 8, scale = 3)
    private BigDecimal precioConIva;
}
