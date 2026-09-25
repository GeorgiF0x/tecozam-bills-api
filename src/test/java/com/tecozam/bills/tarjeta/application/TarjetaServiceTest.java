package com.tecozam.bills.tarjeta.application;

import com.tecozam.bills.auditoria.application.AuditoriaPinService;
import com.tecozam.bills.auditoria.infrastructure.persistence.PinAccesoEventoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioRepository;
import com.tecozam.bills.factura.infrastructure.persistence.TarjetaResumenRepository;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.domain.TarjetaAsignacion;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.tarjeta.dto.AsignarTarjetaRequest;
import com.tecozam.bills.tarjeta.dto.MiTarjetaDTO;
import com.tecozam.bills.tarjeta.dto.TarjetaDTO;
import com.tecozam.bills.tarjeta.dto.UpdateTarjetaRequest;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import com.tecozam.bills.vehiculo.infrastructure.persistence.VehiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TarjetaServiceTest {

    @Mock TarjetaRepository tarjetaRepository;
    @Mock TarjetaAsignacionRepository tarjetaAsignacionRepository;
    @Mock ProveedorRepository proveedorRepository;
    @Mock TrabajadorRepository trabajadorRepository;
    @Mock VehiculoRepository vehiculoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock UsuarioCampoRepository usuarioCampoRepository;
    @Mock AuditoriaPinService auditoriaPinService;
    @Mock TicketRepository ticketRepository;
    @Mock TarjetaResumenRepository tarjetaResumenRepository;
    @Mock PrestamoRepository prestamoRepository;
    @Mock PinAccesoEventoRepository pinAccesoEventoRepository;

    TarjetaService service;

    Tarjeta tarjeta;
    Trabajador trabajador;

    @BeforeEach
    void setUp() {
        service = new TarjetaService(tarjetaRepository, tarjetaAsignacionRepository, proveedorRepository,
                trabajadorRepository, vehiculoRepository, usuarioRepository, usuarioCampoRepository,
                auditoriaPinService, ticketRepository, tarjetaResumenRepository, prestamoRepository,
                pinAccesoEventoRepository);

        Proveedor proveedor = new Proveedor();
        proveedor.setId(1L);
        proveedor.setNombre("Repsol");

        tarjeta = Tarjeta.builder()
                .numeroTarjeta("9999999999999999")
                .alias("Tarjeta georgi pruebas")
                .proveedor(proveedor)
                .estado(EstadoRecurso.DISPONIBLE)
                .activa(true)
                .build();
        tarjeta.setId(10114L);

        trabajador = new Trabajador();
        trabajador.setId(2L);
        trabajador.setNombre("pruebasXXXXXXX");
        trabajador.setApellidos("pruebas");
    }

    @Test
    @DisplayName("asignar con fechaHasta futura: la tarjeta queda con asignacionActual, no 'disponible' (regresion del bug real)")
    void asignarConFechaHastaFutura_dejaAsignacionActualPoblada() {
        when(tarjetaRepository.findById(10114L)).thenReturn(Optional.of(tarjeta));
        when(trabajadorRepository.findById(2L)).thenReturn(Optional.of(trabajador));
        when(tarjetaRepository.save(any(Tarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        TarjetaAsignacion asignacionGuardada = TarjetaAsignacion.builder()
                .tarjeta(tarjeta)
                .trabajador(trabajador)
                .fechaDesde(LocalDate.now())
                .fechaHasta(LocalDate.now().plusMonths(6))
                .build();
        asignacionGuardada.setId(500L);

        // Tras crear la asignacion, una consulta "activa" (nuevo criterio) SI debe encontrarla
        when(tarjetaAsignacionRepository.findActivaByTarjetaId(eq(10114L), any(LocalDate.class)))
                .thenReturn(Optional.empty(), Optional.of(asignacionGuardada));

        AsignarTarjetaRequest request = new AsignarTarjetaRequest(
                2L, null, LocalDate.now(), LocalDate.now().plusMonths(6));

        service.asignar(10114L, request);
        TarjetaDTO dto = service.findById(10114L);

        assertThat(dto.estado()).isEqualTo("PRESTADO");
        assertThat(dto.asignacionActual()).isNotNull();
        assertThat(dto.asignacionActual().trabajadorNombre()).isEqualTo("pruebasXXXXXXX pruebas");
    }

    @Test
    @DisplayName("findMisTarjetas: una asignacion con fechaHasta futura SI debe aparecer en la PWA")
    void findMisTarjetas_incluyeAsignacionConFechaHastaFutura() {
        var usuarioCampo = new com.tecozam.bills.auth.domain.UsuarioCampo();
        usuarioCampo.setUsername("campo1");
        usuarioCampo.setTrabajador(trabajador);
        when(usuarioCampoRepository.findByUsername("campo1")).thenReturn(Optional.of(usuarioCampo));

        TarjetaAsignacion asignacion = TarjetaAsignacion.builder()
                .tarjeta(tarjeta)
                .trabajador(trabajador)
                .fechaDesde(LocalDate.now())
                .fechaHasta(LocalDate.now().plusMonths(6))
                .build();

        when(tarjetaAsignacionRepository.findActivasByTrabajadorId(eq(2L), any(LocalDate.class)))
                .thenReturn(List.of(asignacion));

        List<MiTarjetaDTO> misTarjetas = service.findMisTarjetas("campo1");

        assertThat(misTarjetas).hasSize(1);
        assertThat(misTarjetas.get(0).alias()).isEqualTo("Tarjeta georgi pruebas");
    }

    @Test
    @DisplayName("devolver cierra la asignacion con fecha de AYER, no hoy (bug real: con fecha de hoy seguia contando como activa el resto del dia)")
    void devolver_cierraAsignacionConFechaDeAyer() {
        when(tarjetaRepository.findById(10114L)).thenReturn(Optional.of(tarjeta));
        when(tarjetaRepository.save(any(Tarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        TarjetaAsignacion asignacionActiva = TarjetaAsignacion.builder()
                .tarjeta(tarjeta)
                .trabajador(trabajador)
                .fechaDesde(LocalDate.now())
                .fechaHasta(LocalDate.now().plusMonths(6))
                .build();
        asignacionActiva.setId(500L);

        when(tarjetaAsignacionRepository.findActivaByTarjetaId(eq(10114L), any(LocalDate.class)))
                .thenReturn(Optional.of(asignacionActiva));

        service.devolver(10114L);

        ArgumentCaptor<TarjetaAsignacion> captor = ArgumentCaptor.forClass(TarjetaAsignacion.class);
        verify(tarjetaAsignacionRepository).save(captor.capture());
        assertThat(captor.getValue().getFechaHasta()).isEqualTo(LocalDate.now().minusDays(1));
    }

    @Test
    @DisplayName("update corrige el numero de tarjeta cuando el nuevo numero no esta en uso")
    void update_corrigeNumeroTarjeta() {
        when(tarjetaRepository.findById(10114L)).thenReturn(Optional.of(tarjeta));
        when(tarjetaRepository.existsByNumeroTarjeta("1111111111111111")).thenReturn(false);
        when(proveedorRepository.findById(1L)).thenReturn(Optional.of(tarjeta.getProveedor()));
        when(tarjetaRepository.save(any(Tarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTarjetaRequest request = new UpdateTarjetaRequest("1111111111111111", "Alias nuevo", 1L);
        TarjetaDTO dto = service.update(10114L, request);

        assertThat(dto.numeroTarjeta()).isEqualTo("1111111111111111");
        assertThat(dto.alias()).isEqualTo("Alias nuevo");
    }

    @Test
    @DisplayName("update rechaza el numero de tarjeta si ya lo tiene otra tarjeta distinta")
    void update_rechazaNumeroDuplicado() {
        when(tarjetaRepository.findById(10114L)).thenReturn(Optional.of(tarjeta));
        when(tarjetaRepository.existsByNumeroTarjeta("8888888888888888")).thenReturn(true);

        UpdateTarjetaRequest request = new UpdateTarjetaRequest("8888888888888888", "Alias", 1L);

        assertThatThrownBy(() -> service.update(10114L, request))
                .isInstanceOf(DuplicateResourceException.class);
        verify(tarjetaRepository, never()).save(any(Tarjeta.class));
    }

    @Test
    @DisplayName("eliminar hace borrado logico (softDelete), no borra el registro")
    void eliminar_hacesBorradoLogico() {
        when(tarjetaRepository.findById(10114L)).thenReturn(Optional.of(tarjeta));
        when(tarjetaRepository.save(any(Tarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        service.eliminar(10114L, false);

        assertThat(tarjeta.isEliminado()).isTrue();
        verify(tarjetaRepository).save(tarjeta);
        verify(tarjetaRepository, never()).delete(any(Tarjeta.class));
    }

    @Test
    @DisplayName("eliminarMasivo con un id inexistente lanza ResourceNotFoundException (rollback real a nivel de transaccion de BD)")
    void eliminarMasivo_idInexistente_lanzaExcepcion() {
        when(tarjetaRepository.findById(10114L)).thenReturn(Optional.of(tarjeta));
        when(tarjetaRepository.findById(999L)).thenReturn(Optional.empty());
        when(tarjetaRepository.save(any(Tarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.eliminarMasivo(List.of(10114L, 999L), false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("eliminar con real=true borra fisicamente si la tarjeta no tiene historial")
    void eliminar_real_sinHistorial_borraFisicamente() {
        when(tarjetaRepository.findById(10114L)).thenReturn(Optional.of(tarjeta));
        when(tarjetaAsignacionRepository.existsByTarjetaId(10114L)).thenReturn(false);
        when(ticketRepository.existsByTarjetaId(10114L)).thenReturn(false);
        when(tarjetaResumenRepository.existsByTarjetaId(10114L)).thenReturn(false);
        when(prestamoRepository.existsByTarjetaId(10114L)).thenReturn(false);
        when(pinAccesoEventoRepository.existsByTarjetaId(10114L)).thenReturn(false);

        service.eliminar(10114L, true);

        verify(tarjetaRepository).delete(tarjeta);
        verify(tarjetaRepository, never()).save(any(Tarjeta.class));
    }

    @Test
    @DisplayName("eliminar con real=true rechaza el borrado si la tarjeta tiene historial asociado")
    void eliminar_real_conHistorial_lanzaBusinessException() {
        when(tarjetaRepository.findById(10114L)).thenReturn(Optional.of(tarjeta));
        when(tarjetaAsignacionRepository.existsByTarjetaId(10114L)).thenReturn(false);
        when(ticketRepository.existsByTarjetaId(10114L)).thenReturn(true);

        assertThatThrownBy(() -> service.eliminar(10114L, true))
                .isInstanceOf(BusinessException.class);
        verify(tarjetaRepository, never()).delete(any(Tarjeta.class));
        verify(tarjetaRepository, never()).save(any(Tarjeta.class));
    }
}
