package com.tecozam.bills.centrocoste.application;

import com.tecozam.bills.centrocoste.domain.CentroCoste;
import com.tecozam.bills.centrocoste.infrastructure.persistence.CentroCosteRepository;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
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
class CentroCosteServiceTest {

    @Mock CentroCosteRepository centroCosteRepository;
    @Mock PrestamoRepository prestamoRepository;

    CentroCosteService service;

    CentroCoste centroCoste;

    @BeforeEach
    void setUp() {
        service = new CentroCosteService(centroCosteRepository, prestamoRepository);

        centroCoste = CentroCoste.builder()
                .codigo("CC-01")
                .nombre("Obra pruebas")
                .activo(true)
                .build();
        centroCoste.setId(500L);
    }

    @Test
    @DisplayName("eliminar hace borrado logico (softDelete), no borra el registro")
    void eliminar_hacesBorradoLogico() {
        when(centroCosteRepository.findById(500L)).thenReturn(Optional.of(centroCoste));
        when(centroCosteRepository.save(any(CentroCoste.class))).thenAnswer(inv -> inv.getArgument(0));

        service.eliminar(500L, false);

        assertThat(centroCoste.isEliminado()).isTrue();
        verify(centroCosteRepository).save(centroCoste);
        verify(centroCosteRepository, never()).delete(any(CentroCoste.class));
    }

    @Test
    @DisplayName("eliminar con real=true borra fisicamente si el centro de coste no tiene historial")
    void eliminar_real_sinHistorial_borraFisicamente() {
        when(centroCosteRepository.findById(500L)).thenReturn(Optional.of(centroCoste));
        when(prestamoRepository.existsByCentroCosteId(500L)).thenReturn(false);

        service.eliminar(500L, true);

        verify(centroCosteRepository).delete(centroCoste);
        verify(centroCosteRepository, never()).save(any(CentroCoste.class));
    }

    @Test
    @DisplayName("eliminar con real=true rechaza el borrado si el centro de coste tiene historial asociado")
    void eliminar_real_conHistorial_lanzaBusinessException() {
        when(centroCosteRepository.findById(500L)).thenReturn(Optional.of(centroCoste));
        when(prestamoRepository.existsByCentroCosteId(500L)).thenReturn(true);

        assertThatThrownBy(() -> service.eliminar(500L, true))
                .isInstanceOf(BusinessException.class);
        verify(centroCosteRepository, never()).delete(any(CentroCoste.class));
        verify(centroCosteRepository, never()).save(any(CentroCoste.class));
    }

    @Test
    @DisplayName("eliminarMasivo con un id inexistente lanza ResourceNotFoundException")
    void eliminarMasivo_idInexistente_lanzaExcepcion() {
        when(centroCosteRepository.findById(500L)).thenReturn(Optional.of(centroCoste));
        when(centroCosteRepository.findById(999L)).thenReturn(Optional.empty());
        when(centroCosteRepository.save(any(CentroCoste.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.eliminarMasivo(List.of(500L, 999L), false))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
