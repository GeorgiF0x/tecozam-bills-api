package com.tecozam.bills.ticket.infrastructure.persistence;

import com.tecozam.bills.ticket.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /** Excluye los tickets borrados logicamente (eliminado_en IS NULL). */
    @Query("SELECT t FROM Ticket t WHERE t.eliminadoEn IS NULL")
    List<Ticket> findAllActivos();

    @Query("SELECT t FROM Ticket t WHERE t.estadoCotejo = :estadoCotejo AND t.eliminadoEn IS NULL")
    List<Ticket> findByEstadoCotejo(@Param("estadoCotejo") String estadoCotejo);

    @Query("SELECT t FROM Ticket t WHERE t.estadoCotejo IN :estados AND t.eliminadoEn IS NULL")
    List<Ticket> findByEstadoCotejoIn(@Param("estados") List<String> estados);

    @Query("SELECT t FROM Ticket t WHERE t.trabajador.id = :trabajadorId AND t.eliminadoEn IS NULL")
    List<Ticket> findByTrabajadorId(@Param("trabajadorId") Long trabajadorId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.estadoCotejo IN :estados AND t.eliminadoEn IS NULL")
    long countByEstadoCotejoIn(@Param("estados") List<String> estados);

    /** Tickets cuya operación cotejada pertenece a una factura concreta (NEW-09). */
    List<Ticket> findByOperacionCotejadaFacturaId(Long facturaId);

    /**
     * Tickets activos ya vinculados a una operacion concreta. Se usa para
     * evitar que "Vincular operacion" (accion manual) enlace la misma
     * operacion a dos tickets a la vez.
     */
    @Query("SELECT t FROM Ticket t WHERE t.operacionCotejada.id = :operacionId AND t.eliminadoEn IS NULL")
    List<Ticket> findByOperacionCotejadaIdActivos(@Param("operacionId") Long operacionId);

    /**
     * Posibles duplicados: misma tarjeta + mismo importe en una ventana de
     * minutos estrecha. Pensado para detectar fotos repetidas de la misma
     * compra real (recibo cliente + comprobante fiscal + copia comercio),
     * que de otro modo generan varios tickets que acaban cotejados contra la
     * misma operación de la factura.
     */
    @Query("SELECT t FROM Ticket t WHERE t.numTarjeta4ultimos = :ultimos4 " +
            "AND t.fechaHora BETWEEN :desde AND :hasta " +
            "AND t.importeTotal = :importeTotal " +
            "AND t.eliminadoEn IS NULL")
    List<Ticket> findPosiblesDuplicados(
            @Param("ultimos4") String ultimos4,
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("importeTotal") BigDecimal importeTotal
    );
}
