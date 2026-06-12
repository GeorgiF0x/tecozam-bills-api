package com.tecozam.bills.ticket.infrastructure.persistence;

import com.tecozam.bills.ticket.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findByEstadoCotejo(String estadoCotejo);

    List<Ticket> findByEstadoCotejoIn(List<String> estados);

    List<Ticket> findByTrabajadorId(Long trabajadorId);

    long countByEstadoCotejoIn(List<String> estados);

    /** Tickets cuya operación cotejada pertenece a una factura concreta (NEW-09). */
    List<Ticket> findByOperacionCotejadaFacturaId(Long facturaId);
}
