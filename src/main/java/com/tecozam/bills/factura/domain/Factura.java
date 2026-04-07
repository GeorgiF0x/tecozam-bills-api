package com.tecozam.bills.factura.domain;

import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.shared.infrastructure.persistence.AesEncryptorConverter;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "facturas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(name = "num_factura")
    private String numFactura;

    @Column(name = "fecha")
    private LocalDate fecha;

    @Column(name = "periodo_desde")
    private LocalDate periodoDesde;

    @Column(name = "periodo_hasta")
    private LocalDate periodoHasta;

    @Column(name = "vencimiento")
    private LocalDate vencimiento;

    @Column(name = "num_cuenta")
    private String numCuenta;

    @Column(name = "nif_cliente")
    private String nifCliente;

    @Column(name = "nombre_cliente")
    private String nombreCliente;

    @Column(name = "base_imponible", precision = 12, scale = 2)
    private BigDecimal baseImponible;

    @Column(name = "total_iva", precision = 12, scale = 2)
    private BigDecimal totalIva;

    @Column(name = "total_factura", precision = 12, scale = 2)
    private BigDecimal totalFactura;

    @Column(name = "iban")
    @Convert(converter = AesEncryptorConverter.class)
    private String iban;

    @Column(name = "ruta_pdf")
    private String rutaPdf;

    @Column(name = "creado_en", updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "modificado_en")
    private LocalDateTime modificadoEn;

    @Column(name = "creado_por")
    private String creadoPor;

    @Column(name = "modificado_por")
    private String modificadoPor;

    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FacturaDocumento> documentos = new ArrayList<>();

    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<FacturaConceptoResumen> conceptos = new ArrayList<>();

    @OneToMany(mappedBy = "factura", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

    @OneToMany(mappedBy = "factura")
    @Builder.Default
    private List<Operacion> operaciones = new ArrayList<>();

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
