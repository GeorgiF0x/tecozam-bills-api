package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.domain.UsuarioOficina;
import com.tecozam.bills.auth.dto.CrearUsuarioOficinaRequest;
import com.tecozam.bills.auth.dto.InvitarUsuarioRequest;
import com.tecozam.bills.auth.infrastructure.persistence.InvitacionUsuarioRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
import com.tecozam.bills.shared.infrastructure.email.EmailService;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;

/**
 * Contra MSSQL real (no Mockito): el borrado fisico de un UsuarioOficina con
 * historial de invitaciones (como invitado o como invitador) debe rechazarse,
 * igual que el caso sin historial probado en UsuarioOficinaServiceTest se
 * permite. EmailService mockeado porque el entorno no tiene SMTP real.
 */
@SpringBootTest
@ActiveProfiles("test")
class UsuarioOficinaEliminarHistorialIT {

    private static final String INVITADOR = "sectest_invitador_historial";
    private static final String INVITADO_EMAIL = "sectest_invitado_historial@tecozam.local";

    @Autowired
    private UsuarioOficinaService usuarioOficinaService;

    @Autowired
    private UsuarioOficinaRepository usuarioOficinaRepository;

    @Autowired
    private InvitacionUsuarioRepository invitacionUsuarioRepository;

    @MockBean
    private EmailService emailService;

    @AfterEach
    void limpiar() {
        // shortcut: delete(entity) de JpaRepository ya es transaccional por si
        // mismo (a diferencia de un @Modifying @Query), por eso se evita aqui
        // el deleteByUsuarioId derivado que exige una transaccion externa.
        usuarioOficinaRepository.findByUsername(INVITADO_EMAIL).ifPresent(u -> {
            invitacionUsuarioRepository.findByUsuarioIdAndUsedAtIsNull(u.getId())
                    .forEach(invitacionUsuarioRepository::delete);
            usuarioOficinaRepository.delete(u);
        });
        usuarioOficinaRepository.findByUsername(INVITADOR)
                .ifPresent(usuarioOficinaRepository::delete);
    }

    @Test
    void eliminarReal_rechazaAlInvitadoYAlInvitador_porHistorialDeInvitacion() {
        doNothing().when(emailService).enviarInvitacionAdmin(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());

        usuarioOficinaService.crear(new CrearUsuarioOficinaRequest(
                INVITADOR, "Clave123x", null, "Sectest Invitador", null, "GESTOR"));

        usuarioOficinaService.invitar(
                new InvitarUsuarioRequest("Sectest Invitado", INVITADO_EMAIL), INVITADOR);

        UsuarioOficina invitado = usuarioOficinaRepository.findByUsername(INVITADO_EMAIL).orElseThrow();
        UsuarioOficina invitador = usuarioOficinaRepository.findByUsername(INVITADOR).orElseThrow();

        // Rama existsByUsuarioId: el invitado tiene la invitacion asociada a el mismo.
        assertThatThrownBy(() -> usuarioOficinaService.eliminar(invitado.getId(), true))
                .isInstanceOf(BusinessException.class);
        assertThat(usuarioOficinaRepository.findById(invitado.getId())).isPresent();

        // Rama existsByInvitadoPorId: el invitador tiene la invitacion que el mismo envio.
        assertThatThrownBy(() -> usuarioOficinaService.eliminar(invitador.getId(), true))
                .isInstanceOf(BusinessException.class);
        assertThat(usuarioOficinaRepository.findById(invitador.getId())).isPresent();
    }
}
