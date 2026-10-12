package com.tecozam.bills.ticket.infrastructure.persistence;

import com.tecozam.bills.ticket.domain.Ticket;
import com.tecozam.bills.ticket.domain.TicketLinea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un Ticket puede tener varias TicketLinea (una por foto), en vez de los
 * campos escalares planos de siempre (1 ticket = 1 foto). Ver
 * odd/tasks/ticket-multi-foto.md, T1.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketLineaRepositoryIT {

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketLineaRepository ticketLineaRepository;

    @Test
    @DisplayName("guardar un Ticket con varias lineas las persiste todas, ligadas al ticket")
    void guardaVariasLineasLigadasAlTicket() {
        Ticket ticket = Ticket.builder()
                .origen("OCR")
                .estacion("ESTACION MULTI-FOTO TEST")
                .importeTotal(new BigDecimal("55.00"))
                .estadoCotejo("PENDIENTE")
                .build();

        TicketLinea linea1 = TicketLinea.builder()
                .ticket(ticket)
                .estacion("ESTACION MULTI-FOTO TEST")
                .fechaHora(LocalDateTime.now().minusHours(2))
                .litros(new BigDecimal("20.00"))
                .precioLitro(new BigDecimal("1.750"))
                .importeTotal(new BigDecimal("35.00"))
                .fotoIndice(0)
                .build();

        TicketLinea linea2 = TicketLinea.builder()
                .ticket(ticket)
                .estacion("ESTACION MULTI-FOTO TEST")
                .fechaHora(LocalDateTime.now().minusHours(1))
                .litros(new BigDecimal("11.43"))
                .precioLitro(new BigDecimal("1.750"))
                .importeTotal(new BigDecimal("20.00"))
                .fotoIndice(1)
                .build();

        ticket.getLineas().add(linea1);
        ticket.getLineas().add(linea2);

        Ticket guardado = ticketRepository.save(ticket);

        List<TicketLinea> lineas = ticketLineaRepository.findByTicketId(guardado.getId());
        assertThat(lineas).hasSize(2);
        assertThat(lineas)
                .extracting(TicketLinea::getImporteTotal)
                .containsExactlyInAnyOrder(new BigDecimal("35.00"), new BigDecimal("20.00"));
        assertThat(lineas)
                .allMatch(l -> l.getTicket().getId().equals(guardado.getId()));
    }

    @Test
    @DisplayName("quitar una linea de la lista del ticket la borra (orphanRemoval)")
    void orphanRemovalBorraLineaQuitadaDeLaLista() {
        Ticket ticket = Ticket.builder()
                .origen("OCR")
                .estacion("ESTACION ORPHAN TEST")
                .importeTotal(new BigDecimal("10.00"))
                .estadoCotejo("PENDIENTE")
                .build();

        TicketLinea linea = TicketLinea.builder()
                .ticket(ticket)
                .estacion("ESTACION ORPHAN TEST")
                .importeTotal(new BigDecimal("10.00"))
                .fotoIndice(0)
                .build();

        ticket.getLineas().add(linea);
        Ticket guardado = ticketRepository.saveAndFlush(ticket);
        assertThat(ticketLineaRepository.findByTicketId(guardado.getId())).hasSize(1);

        guardado.getLineas().clear();
        ticketRepository.saveAndFlush(guardado);

        assertThat(ticketLineaRepository.findByTicketId(guardado.getId())).isEmpty();
    }
}
