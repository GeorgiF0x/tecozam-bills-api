package com.tecozam.bills.vehiculo.application;

import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import com.tecozam.bills.vehiculo.domain.CategoriaRecurso;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import com.tecozam.bills.vehiculo.infrastructure.persistence.VehiculoRepository;
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
class VehiculoServiceTest {

    @Mock VehiculoRepository vehiculoRepository;
    @Mock PrestamoRepository prestamoRepository;
    @Mock TarjetaAsignacionRepository tarjetaAsignacionRepository;
    @Mock TicketRepository ticketRepository;

    VehiculoService service;
    Vehiculo vehiculo;

    @BeforeEach
    void setUp() {
        service = new VehiculoService(vehiculoRepository, prestamoRepository, tarjetaAsignacionRepository, ticketRepository);

        vehiculo = Vehiculo.builder()
                .matricula("1234-ABC")
                .categoria(CategoriaRecurso.VEHICULO)
                .tipo("CAMION")
                .estado(EstadoRecurso.DISPONIBLE)
                .activo(true)
                .build();
        vehiculo.setId(50L);
    }

    @Test
    @DisplayName("eliminar hace borrado logico (softDelete) por defecto")
    void eliminar_hacesBorradoLogico() {
        when(vehiculoRepository.findById(50L)).thenReturn(Optional.of(vehiculo));
        when(vehiculoRepository.save(any(Vehiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        service.eliminar(50L, false);

        assertThat(vehiculo.isEliminado()).isTrue();
        verify(vehiculoRepository).save(vehiculo);
        verify(vehiculoRepository, never()).delete(any(Vehiculo.class));
    }

    @Test
    @DisplayName("eliminar con real=true borra fisicamente si no tiene historial")
    void eliminar_real_sinHistorial_borraFisicamente() {
        when(vehiculoRepository.findById(50L)).thenReturn(Optional.of(vehiculo));
        when(prestamoRepository.existsByVehiculoId(50L)).thenReturn(false);
        when(tarjetaAsignacionRepository.existsByVehiculoId(50L)).thenReturn(false);
        when(ticketRepository.existsByVehiculoId(50L)).thenReturn(false);

        service.eliminar(50L, true);

        verify(vehiculoRepository).delete(vehiculo);
        verify(vehiculoRepository, never()).save(any(Vehiculo.class));
    }

    @Test
    @DisplayName("eliminar con real=true rechaza el borrado si tiene historial asociado")
    void eliminar_real_conHistorial_lanzaBusinessException() {
        when(vehiculoRepository.findById(50L)).thenReturn(Optional.of(vehiculo));
        when(prestamoRepository.existsByVehiculoId(50L)).thenReturn(false);
        when(tarjetaAsignacionRepository.existsByVehiculoId(50L)).thenReturn(true);

        assertThatThrownBy(() -> service.eliminar(50L, true))
                .isInstanceOf(BusinessException.class);
        verify(vehiculoRepository, never()).delete(any(Vehiculo.class));
        verify(vehiculoRepository, never()).save(any(Vehiculo.class));
    }

    @Test
    @DisplayName("eliminarMasivo con un id inexistente lanza ResourceNotFoundException")
    void eliminarMasivo_idInexistente_lanzaExcepcion() {
        when(vehiculoRepository.findById(50L)).thenReturn(Optional.of(vehiculo));
        when(vehiculoRepository.findById(999L)).thenReturn(Optional.empty());
        when(vehiculoRepository.save(any(Vehiculo.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.eliminarMasivo(List.of(50L, 999L), false))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
