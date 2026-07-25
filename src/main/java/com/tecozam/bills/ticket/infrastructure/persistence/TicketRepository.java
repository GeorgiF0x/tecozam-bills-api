package com.tecozam.bills.ticket.infrastructure.persistence;

import com.tecozam.bills.ticket.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
