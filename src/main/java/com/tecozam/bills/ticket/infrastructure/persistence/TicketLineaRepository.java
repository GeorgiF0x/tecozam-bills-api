package com.tecozam.bills.ticket.infrastructure.persistence;

import com.tecozam.bills.ticket.domain.TicketLinea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketLineaRepository extends JpaRepository<TicketLinea, Long> {

    List<TicketLinea> findByTicketId(Long ticketId);
}
