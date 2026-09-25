package com.tecozam.bills.trabajador.application;

import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioRepository;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrabajadorServiceTest {

    @Mock TrabajadorRepository trabajadorRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock UsuarioCampoRepository usuarioCampoRepository;
    @Mock UsuarioOficinaRepository usuarioOficinaRepository;
    @Mock PrestamoRepository prestamoRepository;
    @Mock TarjetaAsignacionRepository tarjetaAsignacionRepository;
    @Mock TicketRepository ticketRepository;

    TrabajadorService service;

    Trabajador trabajador;

    @BeforeEach
    void setUp() {
        service = new TrabajadorService(trabajadorRepository, usuarioRepository, usuarioCampoRepository,
                usuarioOficinaRepository, prestamoRepository, tarjetaAsignacionRepository, ticketRepository);

        trabajador = Trabajador.builder()
                .nombre("Georgi")
                .apellidos("Pruebas")
                .email("georgi@example.com")
                .activo(true)
                .build();
        trabajador.setId(20114L);
    }

    @Test
    @DisplayName("eliminar hace borrado logico (softDelete), no borra el registro")
    void eliminar_hacesBorradoLogico() {
        when(trabajadorRepository.findById(20114L)).thenReturn(Optional.of(trabajador));
        when(trabajadorRepository.save(any(Trabajador.class))).thenAnswer(inv -> inv.getArgument(0));

        service.eliminar(20114L, false);

        assertThat(trabajador.isEliminado()).isTrue();
        verify(trabajadorRepository).save(trabajador);
        verify(trabajadorRepository, never()).delete(any(Trabajador.class));
    }

    @Test
    @DisplayName("eliminar con real=true borra fisicamente si el trabajador no tiene historial")
    void eliminar_real_sinHistorial_borraFisicamente() {
        when(trabajadorRepository.findById(20114L)).thenReturn(Optional.of(trabajador));
        when(usuarioRepository.existsByTrabajadorId(20114L)).thenReturn(false);
        when(usuarioCampoRepository.existsByTrabajadorId(20114L)).thenReturn(false);
        when(usuarioOficinaRepository.existsByTrabajadorId(20114L)).thenReturn(false);
        when(prestamoRepository.existsByTrabajadorId(20114L)).thenReturn(false);
        when(tarjetaAsignacionRepository.existsByTrabajadorId(20114L)).thenReturn(false);
        when(ticketRepository.existsByTrabajadorId(20114L)).thenReturn(false);

        service.eliminar(20114L, true);

        verify(trabajadorRepository).delete(trabajador);
        verify(trabajadorRepository, never()).save(any(Trabajador.class));
    }

    @Test
    @DisplayName("eliminar con real=true rechaza el borrado si el trabajador tiene historial asociado")
    void eliminar_real_conHistorial_lanzaBusinessException() {
        when(trabajadorRepository.findById(20114L)).thenReturn(Optional.of(trabajador));
        when(usuarioRepository.existsByTrabajadorId(20114L)).thenReturn(false);
        when(usuarioCampoRepository.existsByTrabajadorId(20114L)).thenReturn(false);
        when(usuarioOficinaRepository.existsByTrabajadorId(20114L)).thenReturn(false);
        when(prestamoRepository.existsByTrabajadorId(20114L)).thenReturn(true);

        assertThatThrownBy(() -> service.eliminar(20114L, true))
                .isInstanceOf(BusinessException.class);
        verify(trabajadorRepository, never()).delete(any(Trabajador.class));
        verify(trabajadorRepository, never()).save(any(Trabajador.class));
    }

    @Test
    @DisplayName("eliminarMasivo con un id inexistente lanza ResourceNotFoundException")
    void eliminarMasivo_idInexistente_lanzaExcepcion() {
        when(trabajadorRepository.findById(20114L)).thenReturn(Optional.of(trabajador));
        when(trabajadorRepository.findById(999L)).thenReturn(Optional.empty());
        when(trabajadorRepository.save(any(Trabajador.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.eliminarMasivo(List.of(20114L, 999L), false))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
