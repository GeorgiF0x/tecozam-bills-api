package com.tecozam.bills.proveedor.application;

import com.tecozam.bills.factura.infrastructure.persistence.FacturaRepository;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.dto.ProveedorDTO;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.tarifa.infrastructure.persistence.TarifaRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
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
class ProveedorServiceTest {

    @Mock ProveedorRepository proveedorRepository;
    @Mock TarifaRepository tarifaRepository;
    @Mock FacturaRepository facturaRepository;
    @Mock TicketRepository ticketRepository;
    @Mock TarjetaRepository tarjetaRepository;

    ProveedorService service;

    Proveedor proveedor;

    @BeforeEach
    void setUp() {
        service = new ProveedorService(proveedorRepository, tarifaRepository, facturaRepository,
                ticketRepository, tarjetaRepository);

        proveedor = Proveedor.builder()
                .codigo("REPSOL")
                .nombre("Repsol")
                .nif("A00000000")
                .activo(true)
                .build();
        proveedor.setId(1L);
    }

    @Test
    @DisplayName("eliminar hace borrado logico (softDelete), no borra el registro")
    void eliminar_hacesBorradoLogico() {
        when(proveedorRepository.findById(1L)).thenReturn(Optional.of(proveedor));
        when(proveedorRepository.save(any(Proveedor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.eliminar(1L, false);

        assertThat(proveedor.isEliminado()).isTrue();
        verify(proveedorRepository).save(proveedor);
        verify(proveedorRepository, never()).delete(any(Proveedor.class));
    }

    @Test
    @DisplayName("eliminar con real=true borra fisicamente si el proveedor no tiene historial")
    void eliminar_real_sinHistorial_borraFisicamente() {
        when(proveedorRepository.findById(1L)).thenReturn(Optional.of(proveedor));
        when(tarifaRepository.existsByProveedorId(1L)).thenReturn(false);
        when(facturaRepository.existsByProveedorId(1L)).thenReturn(false);
        when(ticketRepository.existsByProveedorId(1L)).thenReturn(false);
        when(tarjetaRepository.existsByProveedorId(1L)).thenReturn(false);

        service.eliminar(1L, true);

        verify(proveedorRepository).delete(proveedor);
        verify(proveedorRepository, never()).save(any(Proveedor.class));
    }

    @Test
    @DisplayName("eliminar con real=true rechaza el borrado si el proveedor tiene historial asociado")
    void eliminar_real_conHistorial_lanzaBusinessException() {
        when(proveedorRepository.findById(1L)).thenReturn(Optional.of(proveedor));
        when(tarifaRepository.existsByProveedorId(1L)).thenReturn(false);
        when(facturaRepository.existsByProveedorId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.eliminar(1L, true))
                .isInstanceOf(BusinessException.class);
        verify(proveedorRepository, never()).delete(any(Proveedor.class));
        verify(proveedorRepository, never()).save(any(Proveedor.class));
    }

    @Test
    @DisplayName("eliminarMasivo con un id inexistente lanza ResourceNotFoundException")
    void eliminarMasivo_idInexistente_lanzaExcepcion() {
        when(proveedorRepository.findById(1L)).thenReturn(Optional.of(proveedor));
        when(proveedorRepository.findById(999L)).thenReturn(Optional.empty());
        when(proveedorRepository.save(any(Proveedor.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.eliminarMasivo(List.of(1L, 999L), false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findAll excluye proveedores eliminados")
    void findAll_excluyeEliminados() {
        when(proveedorRepository.findByActivoTrueAndEliminadoEnIsNull()).thenReturn(List.of(proveedor));

        List<ProveedorDTO> resultado = service.findAll();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).codigo()).isEqualTo("REPSOL");
    }
}
