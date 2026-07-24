package com.tecozam.bills.ticket.infrastructure.scheduler;

import com.tecozam.bills.ticket.application.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketScheduler {

    private final TicketService ticketService;

    @Scheduled(cron = "0 30 8 * * *")
    public void escalarTicketsAntiguos() {
        log.info("Iniciando escalado de tickets sin cotejar hace más de un mes");
        int escalados = ticketService.escalarTicketsAntiguos();
        log.info("Escalado completado: {} tickets escalados a INCIDENCIA", escalados);
    }
}
