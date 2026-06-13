package com.tecozam.bills.auditoria.application;

import com.tecozam.bills.auditoria.domain.MetodoPinAcceso;
import com.tecozam.bills.auditoria.domain.PinAccesoEvento;
import com.tecozam.bills.auditoria.domain.ResultadoPinAcceso;
import com.tecozam.bills.auditoria.infrastructure.persistence.PinAccesoEventoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditoriaPinServiceTest {

    @Mock PinAccesoEventoRepository repo;

    @Test
    @DisplayName("registrar() persiste un PinAccesoEvento con los datos pasados")
    void registrar_persisteEvento() {
        when(repo.save(any(PinAccesoEvento.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditoriaPinService svc = new AuditoriaPinService(repo);
        svc.registrar(42L, 99L, MetodoPinAcceso.BIOMETRIA, ResultadoPinAcceso.OK, "1.2.3.4", "Mozilla/5.0");

        ArgumentCaptor<PinAccesoEvento> cap = ArgumentCaptor.forClass(PinAccesoEvento.class);
        verify(repo).save(cap.capture());
        PinAccesoEvento e = cap.getValue();

        assertThat(e.getUsuarioCampoId()).isEqualTo(42L);
        assertThat(e.getTarjetaId()).isEqualTo(99L);
        assertThat(e.getMetodo()).isEqualTo(MetodoPinAcceso.BIOMETRIA);
        assertThat(e.getResultado()).isEqualTo(ResultadoPinAcceso.OK);
        assertThat(e.getIp()).isEqualTo("1.2.3.4");
        assertThat(e.getUserAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    @DisplayName("registrar() acepta resultado FAILED y método PASSWORD")
    void registrar_failed_password() {
        when(repo.save(any(PinAccesoEvento.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditoriaPinService svc = new AuditoriaPinService(repo);
        svc.registrar(42L, 99L, MetodoPinAcceso.PASSWORD, ResultadoPinAcceso.FAILED, "1.2.3.4", "curl/8.0");

        ArgumentCaptor<PinAccesoEvento> cap = ArgumentCaptor.forClass(PinAccesoEvento.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getMetodo()).isEqualTo(MetodoPinAcceso.PASSWORD);
        assertThat(cap.getValue().getResultado()).isEqualTo(ResultadoPinAcceso.FAILED);
    }
}
