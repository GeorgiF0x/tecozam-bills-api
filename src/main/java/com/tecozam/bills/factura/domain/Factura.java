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

    /**
     * Avisos de plausibilidad detectados por {@code FacturaImportValidator} al
     * importar (precio/litro fuera de rango, importe que no cuadra con
     * cantidad x precio, fecha fuera del periodo de la factura, etc). Varias
     * lineas separadas por salto de linea. Null si no se detecto ningun aviso.
     * No bloquea la importacion — es para revision humana, ya que un formato
     * de factura nuevo no visto antes puede hacer que el parser lea mal un
     * campo en silencio sin lanzar ninguna excepcion.
     */
    @Column(name = "avisos_import", columnDefinition = "NVARCHAR(MAX)")
    private String avisosImport;

    /**
     * Cuando la factura se importo con el modo LLM (ver
     * odd/tasks/import-llm-switch.md) y {@code FacturaImportValidator} detecto
     * avisos de plausibilidad, esta factura queda excluida del cotejo
     * automatico (ver {@code OperacionRepository.findParaCotejo*}) hasta que
     * un humano la revise. En modo regex (por defecto) siempre es false: el
     * comportamiento de avisos no bloqueantes no cambia.
     */
    @Column(name = "requiere_revision_manual", nullable = false)
    @Builder.Default
    private boolean requiereRevisionManual = false;

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
        String currentUser = resolveCurrentUser();
        if (this.creadoPor == null) {
            this.creadoPor = currentUser;
        }
        this.modificadoPor = currentUser;
    }

    @PreUpdate
    protected void onPreUpdate() {
        this.modificadoEn = LocalDateTime.now();
        this.modificadoPor = resolveCurrentUser();
    }

    /**
     * Resuelve el usuario autenticado en la sesión actual (mismo criterio que
     * {@link com.tecozam.bills.shared.domain.AuditableEntity}). Factura no
     * extiende esa clase porque introduciría la columna eliminado_en (borrado
     * lógico) sin migración — se duplica aquí la mínima lógica necesaria.
     */
    private static String resolveCurrentUser() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        return "SYSTEM";
    }
}
