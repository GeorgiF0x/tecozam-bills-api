package com.tecozam.bills.factura.domain;

import com.tecozam.bills.tarjeta.domain.Tarjeta;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tarjeta_resumenes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TarjetaResumen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "factura_id", nullable = false)
    private Factura factura;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "tarjeta_id")
    private Tarjeta tarjeta;

    @Column(name = "num_tarjeta")
    private String numTarjeta;

    @Column(name = "alias")
    private String alias;

    @Column(name = "conductor")
    private String conductor;

    @OneToMany(mappedBy = "tarjetaResumen", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TarjetaResumenConcepto> conceptos = new ArrayList<>();

    @OneToMany(mappedBy = "tarjetaResumen", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Operacion> operaciones = new ArrayList<>();
}
