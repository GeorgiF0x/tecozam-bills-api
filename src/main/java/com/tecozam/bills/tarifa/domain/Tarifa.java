package com.tecozam.bills.tarifa.domain;

import com.tecozam.bills.proveedor.domain.Proveedor;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tarifas")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class Tarifa {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(name = "codigo_tarifa", length = 50)
    private String codigoTarifa;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDate vigenteDesde;

    @Column(name = "vigente_hasta")
    private LocalDate vigenteHasta;

    @Column(name = "observaciones", length = 500)
    private String observaciones;

    @OneToMany(mappedBy = "tarifa", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TarifaPrecio> precios = new ArrayList<>();

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "modificado_en")
    private LocalDateTime modificadoEn;

    @PrePersist
    protected void onPrePersist() {
        creadoEn = LocalDateTime.now();
        modificadoEn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onPreUpdate() {
        modificadoEn = LocalDateTime.now();
    }
}
