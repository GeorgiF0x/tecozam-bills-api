package com.tecozam.bills.alerta.infrastructure.scheduler;

import com.tecozam.bills.alerta.domain.AlertaPrestamo;
import com.tecozam.bills.alerta.infrastructure.persistence.AlertaPrestamoRepository;
import com.tecozam.bills.prestamo.domain.Prestamo;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlertaScheduler {

    private final PrestamoRepository prestamoRepository;
    private final AlertaPrestamoRepository alertaPrestamoRepository;

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void verificarVencimientos() {
        log.info("Iniciando verificación de vencimientos de préstamos");
        LocalDate hoy = LocalDate.now();

        List<Prestamo> activos = prestamoRepository.findByEstado("ACTIVO");
        int procesados = 0;

        for (Prestamo p : activos) {
            if (p.getFechaFinPrevista() == null) continue;

            // Las alertas siguen siendo por día: comparamos solo la parte de fecha
            // del LocalDateTime con la fecha de hoy.
            long dias = ChronoUnit.DAYS.between(hoy, p.getFechaFinPrevista().toLocalDate());

            if (dias == 3) crearAlertaSiNoExiste(p, "TRES_DIAS_ANTES", hoy);
            if (dias == 1) crearAlertaSiNoExiste(p, "UN_DIA_ANTES", hoy);
            if (dias == 0) crearAlertaSiNoExiste(p, "MISMO_DIA", hoy);
            if (dias < 0) {
                crearAlertaSiNoExiste(p, "VENCIDO", hoy);
                p.setEstado("VENCIDO");
                prestamoRepository.save(p);
            }
            procesados++;
        }

        log.info("Verificación completada: {} préstamos procesados", procesados);
    }

    private void crearAlertaSiNoExiste(Prestamo p, String tipo, LocalDate fecha) {
        boolean yaExiste = alertaPrestamoRepository
                .existsByPrestamoIdAndTipoAlertaAndFechaAlerta(p.getId(), tipo, fecha);
        if (!yaExiste) {
            AlertaPrestamo alerta = new AlertaPrestamo();
            alerta.setPrestamo(p);
            alerta.setTipoAlerta(tipo);
            alerta.setFechaAlerta(fecha);
            alerta.setMensaje("Préstamo " + p.getTipoRecurso() + " vence " + p.getFechaFinPrevista().toLocalDate());
            alerta.setLeida(false);
            alerta.setEmailEnviado(false);
            alerta.setCreadoEn(LocalDateTime.now());
            alertaPrestamoRepository.save(alerta);
            log.debug("Alerta creada: tipo={}, prestamoId={}", tipo, p.getId());
        }
    }
}
