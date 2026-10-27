package com.tecozam.bills.auth.application;

import com.tecozam.bills.auditoria.infrastructure.persistence.PinAccesoEventoRepository;
import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRegistro;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.trabajador.application.TrabajadorResolver;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import com.tecozam.bills.webauthn.infrastructure.persistence.WebauthnCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioCampoServiceTest {

    @Mock UsuarioCampoRepository usuarioCampoRepository;
    @Mock TrabajadorRepository trabajadorRepository;
    @Mock TrabajadorResolver trabajadorResolver;
    @Mock PasswordEncoder passwordEncoder;
    @Mock WebauthnCredentialRepository webauthnCredentialRepository;
    @Mock PinAccesoEventoRepository pinAccesoEventoRepository;

    UsuarioCampoService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioCampoService(usuarioCampoRepository, trabajadorRepository, trabajadorResolver,
                passwordEncoder, webauthnCredentialRepository, pinAccesoEventoRepository);
    }

    @Test
    @DisplayName("eliminar hace borrado logico (softDelete), no borra el registro")
    void eliminar_hacesBorradoLogico() {
        UsuarioCampo usuario = UsuarioCampo.builder()
                .username("campo1").activo(true)
                .estadoRegistro(EstadoRegistro.ACTIVO).build();
        usuario.setId(600L);

        when(usuarioCampoRepository.findById(600L)).thenReturn(Optional.of(usuario));
        when(usuarioCampoRepository.save(any(UsuarioCampo.class))).thenAnswer(inv -> inv.getArgument(0));

        service.eliminar(600L, false);

        assertThat(usuario.isEliminado()).isTrue();
        verify(usuarioCampoRepository).save(usuario);
        verify(usuarioCampoRepository, never()).delete(any(UsuarioCampo.class));
    }

    @Test
    @DisplayName("eliminar con real=true borra fisicamente si el usuario no tiene historial")
    void eliminar_real_sinHistorial_borraFisicamente() {
        UsuarioCampo usuario = UsuarioCampo.builder()
                .username("campo2").activo(true)
                .estadoRegistro(EstadoRegistro.ACTIVO).build();
        usuario.setId(601L);

        when(usuarioCampoRepository.findById(601L)).thenReturn(Optional.of(usuario));
        when(webauthnCredentialRepository.existsByUsuarioCampoId(601L)).thenReturn(false);
        when(pinAccesoEventoRepository.existsByUsuarioCampoId(601L)).thenReturn(false);

        service.eliminar(601L, true);

        verify(usuarioCampoRepository).delete(usuario);
        verify(usuarioCampoRepository, never()).save(any(UsuarioCampo.class));
    }

    @Test
    @DisplayName("eliminar con real=true rechaza el borrado si el usuario tiene historial asociado")
    void eliminar_real_conHistorial_lanzaBusinessException() {
        UsuarioCampo usuario = UsuarioCampo.builder()
                .username("campo3").activo(true)
                .estadoRegistro(EstadoRegistro.ACTIVO).build();
        usuario.setId(602L);

        when(usuarioCampoRepository.findById(602L)).thenReturn(Optional.of(usuario));
        when(webauthnCredentialRepository.existsByUsuarioCampoId(602L)).thenReturn(true);

        assertThatThrownBy(() -> service.eliminar(602L, true))
                .isInstanceOf(BusinessException.class);
        verify(usuarioCampoRepository, never()).delete(any(UsuarioCampo.class));
        verify(usuarioCampoRepository, never()).save(any(UsuarioCampo.class));
    }
}
