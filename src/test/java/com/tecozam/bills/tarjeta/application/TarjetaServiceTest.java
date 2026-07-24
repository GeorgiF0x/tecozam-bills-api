package com.tecozam.bills.tarjeta.application;

import com.tecozam.bills.auditoria.application.AuditoriaPinService;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioRepository;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.domain.TarjetaAsignacion;
import com.tecozam.bills.tarjeta.dto.AsignarTarjetaRequest;
import com.tecozam.bills.tarjeta.dto.MiTarjetaDTO;
import com.tecozam.bills.tarjeta.dto.TarjetaDTO;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

    TarjetaService service;

    Tarjeta tarjeta;
    Trabajador trabajador;

    @BeforeEach
    void setUp() {
        service = new TarjetaService(tarjetaRepository, tarjetaAsignacionRepository, proveedorRepository,
                trabajadorRepository, vehiculoRepository, usuarioRepository, usuarioCampoRepository,
                auditoriaPinService);

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
}
