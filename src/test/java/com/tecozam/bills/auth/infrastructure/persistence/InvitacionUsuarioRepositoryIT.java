package com.tecozam.bills.auth.infrastructure.persistence;

import com.tecozam.bills.auth.domain.InvitacionUsuario;
import com.tecozam.bills.auth.domain.UsuarioOficina;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import com.tecozam.bills.shared.domain.enums.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvitacionUsuarioRepositoryIT {

    @Autowired
    private InvitacionUsuarioRepository invitacionUsuarioRepository;

    @Autowired
    private UsuarioOficinaRepository usuarioOficinaRepository;

    @Test
    @DisplayName("findByTokenHash encuentra la invitacion por su hash exacto")
    void findByTokenHash_encuentraPorHashExacto() {
        UsuarioOficina invitador = usuarioOficinaRepository.save(usuarioOficina("invitador-" + System.nanoTime()));
        UsuarioOficina invitado = usuarioOficinaRepository.save(usuarioOficina("invitado-" + System.nanoTime()));

        InvitacionUsuario invitacion = invitacionUsuarioRepository.save(InvitacionUsuario.builder()
                .usuario(invitado)
                .invitadoPor(invitador)
                .tokenHash("hash-abc-123")
                .expiresAt(LocalDateTime.now().plusHours(72))
                .build());

        Optional<InvitacionUsuario> encontrada = invitacionUsuarioRepository.findByTokenHash("hash-abc-123");

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getId()).isEqualTo(invitacion.getId());
        assertThat(encontrada.get().getUsuario().getId()).isEqualTo(invitado.getId());

        assertThat(invitacionUsuarioRepository.findByTokenHash("hash-que-no-existe")).isEmpty();
    }

    @Test
    @DisplayName("findByUsuarioIdAndUsedAtIsNull solo devuelve invitaciones pendientes de ese usuario")
    void findByUsuarioIdAndUsedAtIsNull_soloDevuelvePendientes() {
        UsuarioOficina invitador = usuarioOficinaRepository.save(usuarioOficina("invitador2-" + System.nanoTime()));
        UsuarioOficina invitado = usuarioOficinaRepository.save(usuarioOficina("invitado2-" + System.nanoTime()));

        InvitacionUsuario pendiente = invitacionUsuarioRepository.save(InvitacionUsuario.builder()
                .usuario(invitado)
                .invitadoPor(invitador)
                .tokenHash("hash-pendiente")
                .expiresAt(LocalDateTime.now().plusHours(72))
                .build());

        invitacionUsuarioRepository.save(InvitacionUsuario.builder()
                .usuario(invitado)
                .invitadoPor(invitador)
                .tokenHash("hash-usada")
                .expiresAt(LocalDateTime.now().plusHours(72))
                .usedAt(LocalDateTime.now())
                .build());

        var pendientes = invitacionUsuarioRepository.findByUsuarioIdAndUsedAtIsNull(invitado.getId());

        assertThat(pendientes)
                .extracting(InvitacionUsuario::getId)
                .containsExactly(pendiente.getId());
    }

    private UsuarioOficina usuarioOficina(String username) {
        return UsuarioOficina.builder()
                .username(username)
                .password("hash")
                .rol(Rol.ADMIN)
                .activo(true)
                .estadoRegistro(EstadoRegistro.ACTIVO)
                .build();
    }
}
