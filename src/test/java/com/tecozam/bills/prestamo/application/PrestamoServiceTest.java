package com.tecozam.bills.prestamo.application;

import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioRepository;
import com.tecozam.bills.centrocoste.infrastructure.persistence.CentroCosteRepository;
import com.tecozam.bills.prestamo.dto.RecursoDisponibleDTO;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import com.tecozam.bills.vehiculo.domain.CategoriaRecurso;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import com.tecozam.bills.vehiculo.infrastructure.persistence.VehiculoRepository;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrestamoServiceTest {

    @Mock PrestamoRepository prestamoRepository;
    @Mock TrabajadorRepository trabajadorRepository;
    @Mock CentroCosteRepository centroCosteRepository;
    @Mock TarjetaRepository tarjetaRepository;
    @Mock ViatRepository viatRepository;
    @Mock VehiculoRepository vehiculoRepository;
    @Mock UsuarioCampoRepository usuarioCampoRepository;
    @Mock UsuarioRepository usuarioRepository;

    PrestamoService service;

    @BeforeEach
    void setUp() {
        service = new PrestamoService(
                prestamoRepository,
                trabajadorRepository,
                centroCosteRepository,
                tarjetaRepository,
                viatRepository,
                vehiculoRepository,
                usuarioCampoRepository,
                usuarioRepository);
    }

    @Test
    @DisplayName("findRecursosDisponibles(VEHICULO) no devuelve vehiculos rechazados (borrado logico)")
    void findRecursosDisponibles_vehiculo_excluyeEliminados() {
        Vehiculo disponible = Vehiculo.builder()
                .matricula("1234-ABC")
                .categoria(CategoriaRecurso.VEHICULO)
                .estado(EstadoRecurso.DISPONIBLE)
                .activo(true)
                .build();
        disponible.setId(1L);

        // Simula un vehiculo de sustitucion que el gestor rechazo (softDelete):
        // su prestamo se cancelo y liberarRecurso() volvio a poner su estado en
        // DISPONIBLE, pero sigue eliminado y no debe ofrecerse de nuevo.
        when(vehiculoRepository.findByEstadoAndActivoTrueAndEliminadoEnIsNull(EstadoRecurso.DISPONIBLE))
                .thenReturn(List.of(disponible));

        List<RecursoDisponibleDTO> result = service.findRecursosDisponibles("VEHICULO");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }
}
