package com.tecozam.bills.viat.application;

import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.viat.domain.Viat;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
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
class ViatServiceTest {

    @Mock ViatRepository viatRepository;
    @Mock PrestamoRepository prestamoRepository;

    ViatService service;

    Viat viat;

    @BeforeEach
    void setUp() {
        service = new ViatService(viatRepository, prestamoRepository);

        viat = Viat.builder()
                .codigo("VIAT-TEST-001")
                .numeroSerie("SN-000001")
                .descripcion("Viat de pruebas")
                .estado(EstadoRecurso.DISPONIBLE)
                .activo(true)
                .build();
        viat.setId(500L);
    }

    @Test
    @DisplayName("eliminar hace borrado logico (softDelete), no borra el registro")
    void eliminar_hacesBorradoLogico() {
        when(viatRepository.findById(500L)).thenReturn(Optional.of(viat));
        when(viatRepository.save(any(Viat.class))).thenAnswer(inv -> inv.getArgument(0));

        service.eliminar(500L, false);

        assertThat(viat.isEliminado()).isTrue();
        verify(viatRepository).save(viat);
        verify(viatRepository, never()).delete(any(Viat.class));
    }

    @Test
    @DisplayName("eliminar con real=true borra fisicamente si el viat no tiene historial")
    void eliminar_real_sinHistorial_borraFisicamente() {
        when(viatRepository.findById(500L)).thenReturn(Optional.of(viat));
        when(prestamoRepository.existsByViatId(500L)).thenReturn(false);

        service.eliminar(500L, true);

        verify(viatRepository).delete(viat);
        verify(viatRepository, never()).save(any(Viat.class));
    }

    @Test
    @DisplayName("eliminar con real=true rechaza el borrado si el viat tiene historial asociado")
    void eliminar_real_conHistorial_lanzaBusinessException() {
        when(viatRepository.findById(500L)).thenReturn(Optional.of(viat));
        when(prestamoRepository.existsByViatId(500L)).thenReturn(true);

        assertThatThrownBy(() -> service.eliminar(500L, true))
                .isInstanceOf(BusinessException.class);
        verify(viatRepository, never()).delete(any(Viat.class));
        verify(viatRepository, never()).save(any(Viat.class));
    }

    @Test
    @DisplayName("eliminarMasivo con un id inexistente lanza ResourceNotFoundException")
    void eliminarMasivo_idInexistente_lanzaExcepcion() {
        when(viatRepository.findById(500L)).thenReturn(Optional.of(viat));
        when(viatRepository.findById(999L)).thenReturn(Optional.empty());
        when(viatRepository.save(any(Viat.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.eliminarMasivo(List.of(500L, 999L), false))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
