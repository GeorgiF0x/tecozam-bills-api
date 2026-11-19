package com.tecozam.bills.vehiculo.application;

import com.tecozam.bills.prestamo.domain.Prestamo;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.tarjeta.domain.TarjetaAsignacion;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.vehiculo.domain.CategoriaRecurso;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import com.tecozam.bills.vehiculo.dto.CreateMiVehiculoRequest;
import com.tecozam.bills.vehiculo.dto.VehiculoArchivadoDTO;
import com.tecozam.bills.vehiculo.dto.VehiculoDTO;
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
import static org.mockito.ArgumentMatchers.eq;
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

    @Test
    @DisplayName("findArchivados devuelve los vehiculos eliminados con sus vinculos activos (tarjeta y prestamo)")
    void findArchivados_devuelveVinculosActivos() {
        vehiculo.softDelete();
        Trabajador trabajador = Trabajador.builder().nombre("Juan Perez").build();
        TarjetaAsignacion asignacion = TarjetaAsignacion.builder().trabajador(trabajador).vehiculo(vehiculo).build();
        Prestamo prestamo = Prestamo.builder().trabajador(trabajador).vehiculo(vehiculo).estado("ACTIVO").build();

        when(vehiculoRepository.findByEliminadoEnIsNotNull()).thenReturn(List.of(vehiculo));
        when(tarjetaAsignacionRepository.findActivasByVehiculoId(eq(50L), any())).thenReturn(List.of(asignacion));
        when(prestamoRepository.findByVehiculoIdAndEstadoIn(eq(50L), any())).thenReturn(List.of(prestamo));

        List<VehiculoArchivadoDTO> result = service.findArchivados();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).matricula()).isEqualTo("1234-ABC");
        assertThat(result.get(0).vinculosActivos())
                .containsExactly("Tarjeta asignada a Juan Perez", "Préstamo activo de Juan Perez");
    }

    @Test
    @DisplayName("crearMiVehiculo sin categoria da de alta un VEHICULO por matricula (comportamiento previo)")
    void crearMiVehiculo_sinCategoria_creaVehiculoPorMatricula() {
        when(vehiculoRepository.existsByMatriculaAndEliminadoEnIsNull("9999-TST")).thenReturn(false);
        when(vehiculoRepository.save(any(Vehiculo.class))).thenAnswer(inv -> {
            Vehiculo v = inv.getArgument(0);
            v.setId(77L);
            return v;
        });

        VehiculoDTO result = service.crearMiVehiculo(
                new CreateMiVehiculoRequest("9999-TST", null, null, "Alquiler findes"));

        assertThat(result.matricula()).isEqualTo("9999-TST");
        assertThat(result.categoria()).isEqualTo("VEHICULO");
        assertThat(result.creadoPorCampo()).isTrue();
    }

    @Test
    @DisplayName("crearMiVehiculo con categoria INDUSTRIAL_MAQUINARIA da de alta por codigo de obra")
    void crearMiVehiculo_industrial_creaPorCodigoObra() {
        when(vehiculoRepository.existsByCodigoObraAndEliminadoEnIsNull("OBRA-99")).thenReturn(false);
        when(vehiculoRepository.save(any(Vehiculo.class))).thenAnswer(inv -> {
            Vehiculo v = inv.getArgument(0);
            v.setId(78L);
            return v;
        });

        VehiculoDTO result = service.crearMiVehiculo(
                new CreateMiVehiculoRequest(null, "OBRA-99", "INDUSTRIAL_MAQUINARIA", "Grua subcontratada"));

        assertThat(result.codigoObra()).isEqualTo("OBRA-99");
        assertThat(result.categoria()).isEqualTo("INDUSTRIAL_MAQUINARIA");
        assertThat(result.creadoPorCampo()).isTrue();
    }

    @Test
    @DisplayName("crearMiVehiculo industrial sin codigo de obra lanza BusinessException")
    void crearMiVehiculo_industrial_sinCodigoObra_lanzaExcepcion() {
        assertThatThrownBy(() -> service.crearMiVehiculo(
                new CreateMiVehiculoRequest(null, null, "INDUSTRIAL_MAQUINARIA", null)))
                .isInstanceOf(BusinessException.class);

        verify(vehiculoRepository, never()).save(any(Vehiculo.class));
    }

    @Test
    @DisplayName("crearMiVehiculo industrial con codigo de obra duplicado lanza DuplicateResourceException")
    void crearMiVehiculo_industrial_codigoObraDuplicado_lanzaExcepcion() {
        when(vehiculoRepository.existsByCodigoObraAndEliminadoEnIsNull("OBRA-99")).thenReturn(true);

        assertThatThrownBy(() -> service.crearMiVehiculo(
                new CreateMiVehiculoRequest(null, "OBRA-99", "INDUSTRIAL_MAQUINARIA", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }
}
