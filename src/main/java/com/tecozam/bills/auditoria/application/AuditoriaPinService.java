package com.tecozam.bills.auditoria.application;

import com.tecozam.bills.auditoria.domain.MetodoPinAcceso;
import com.tecozam.bills.auditoria.domain.PinAccesoEvento;
import com.tecozam.bills.auditoria.domain.ResultadoPinAcceso;
import com.tecozam.bills.auditoria.infrastructure.persistence.PinAccesoEventoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra eventos de acceso al PIN de tarjeta en {@code auditoria_pin_acceso}.
 * Cubre task #47.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditoriaPinService {

    private final PinAccesoEventoRepository repo;

    /**
     * Persiste un evento de auditoría. {@link Propagation#REQUIRES_NEW} para
     * que en caso de rollback de la transacción caller el evento (sobre todo
     * el FAILED) se mantenga.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long usuarioCampoId, Long tarjetaId, MetodoPinAcceso metodo,
                          ResultadoPinAcceso resultado, String ip, String userAgent) {
        PinAccesoEvento evento = PinAccesoEvento.builder()
                .usuarioCampoId(usuarioCampoId)
                .tarjetaId(tarjetaId)
                .metodo(metodo)
                .resultado(resultado)
                .ip(ip != null ? ip : "")
                .userAgent(userAgent != null ? userAgent : "")
                .build();
        repo.save(evento);
        log.debug("Auditoría PIN: usuario={} tarjeta={} metodo={} resultado={}",
                usuarioCampoId, tarjetaId, metodo, resultado);
    }
}
