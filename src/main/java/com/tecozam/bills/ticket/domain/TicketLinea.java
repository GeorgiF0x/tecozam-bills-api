package com.tecozam.bills.ticket.domain;

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
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una linea (operacion) extraida de UNA de las N fotos que componen un
 * {@link Ticket}. Antes de esta entidad, Ticket tenia estos mismos campos
 * como escalares planos (1 ticket = 1 foto = 1 operacion); ahora un ticket
 * puede tener varias lineas, una por foto, para tickets largos que el
 * cliente fotografia en varias partes.
 *
 * No reutiliza {@link com.tecozam.bills.factura.domain.Operacion} aposta:
 * esa entidad pertenece a una Factura+TarjetaResumen reales (ambas FK
 * nullable=false) y no tiene sentido en el momento de captura en campo,
 * donde todavia no existe ninguna factura con la que cotejar.
 */
@Entity
@Table(name = "ticket_lineas")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Excluida de toString/equals/hashCode: Ticket.lineas apunta de vuelta a
     * cada TicketLinea, y ambas clases usan @Data (toString/equals/hashCode
     * generados) — sin excluir este lado se entra en recursion infinita
     * (StackOverflowError real, visto en pruebas con T3) en cuanto Ticket
     * tiene lineas cargadas Y se intenta imprimir/comparar cualquiera de
     * las dos.
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(name = "estacion")
    private String estacion;

    @Column(name = "fecha_hora")
    private LocalDateTime fechaHora;

    @Column(name = "producto")
    private String producto;

    @Column(name = "num_recibo")
    private String numRecibo;

    @Column(name = "matricula")
    private String matricula;

    @Column(name = "kms")
    private Integer kms;

    @Column(name = "litros", precision = 10, scale = 2)
    private BigDecimal litros;

    @Column(name = "precio_litro", precision = 8, scale = 3)
    private BigDecimal precioLitro;

    @Column(name = "importe_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal importeTotal;

    /**
     * Indice (0-based) de la foto de origen dentro del conjunto de fotos
     * subidas para este ticket. Permite trazar que linea vino de que foto;
     * el almacenamiento real de las fotos (T3) rellena este campo.
     */
    @Column(name = "foto_indice")
    private Integer fotoIndice;

    /** Ruta/URL de la foto de origen (T3: ya se persiste, antes se descartaba). */
    @Column(name = "foto_url", length = 500)
    private String fotoUrl;
}
