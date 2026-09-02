package com.tecozam.bills.ticket.application;

import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioRepository;
import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.domain.TarjetaAsignacion;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.ticket.domain.Ticket;
import com.tecozam.bills.ticket.dto.CreateTicketManualRequest;
import com.tecozam.bills.ticket.dto.CreateTicketOcrValidadoRequest;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import com.tecozam.bills.vehiculo.domain.CategoriaRecurso;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import com.tecozam.bills.vehiculo.infrastructure.persistence.VehiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class TicketServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock OperacionRepository operacionRepository;
    @Mock ProveedorRepository proveedorRepository;
    @Mock TrabajadorRepository trabajadorRepository;
    @Mock TarjetaRepository tarjetaRepository;
    @Mock TarjetaAsignacionRepository tarjetaAsignacionRepository;
    @Mock VehiculoRepository vehiculoRepository;
    @Mock UsuarioRepository usuarioRepository;
    @Mock UsuarioCampoRepository usuarioCampoRepository;

    TicketService service;

    Trabajador trabajador;
    Tarjeta tarjeta;
    Vehiculo vehiculo;

    @BeforeEach
    void setUp() {
        service = new TicketService(ticketRepository, operacionRepository, proveedorRepository,
                trabajadorRepository, tarjetaRepository, tarjetaAsignacionRepository, vehiculoRepository,
                usuarioRepository, usuarioCampoRepository);

        trabajador = new Trabajador();
        trabajador.setId(2L);

        tarjeta = Tarjeta.builder().numeroTarjeta("708011008022419012").alias("X9371509K").build();
        tarjeta.setId(200L);

        vehiculo = new Vehiculo();
        vehiculo.setId(1L);
        vehiculo.setMatricula("1234ABC");
    }

    @Test
    @DisplayName("createOcrValidado rellena numTarjeta4ultimos con los ultimos 4 digitos de la tarjeta (bug real: se guardaba null, rompiendo el cotejo por lote sin filtro de tarjeta)")
    void createOcrValidado_rellenaNumTarjeta4ultimos() {
        UsuarioCampo campo = new UsuarioCampo();
        campo.setUsername("campo1");
        campo.setTrabajador(trabajador);
        when(usuarioCampoRepository.findByUsername("campo1")).thenReturn(Optional.of(campo));

        when(tarjetaRepository.findById(200L)).thenReturn(Optional.of(tarjeta));
        when(tarjetaAsignacionRepository.findActivaByTarjetaIdAndTrabajadorId(eq(200L), eq(2L), any(LocalDate.class)))
                .thenReturn(Optional.of(new TarjetaAsignacion()));
        when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(vehiculo));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(operacionRepository.findParaCotejoConTarjeta(any(), any(), any())).thenReturn(List.of());

        CreateTicketOcrValidadoRequest request = new CreateTicketOcrValidadoRequest(
                200L, null, CategoriaRecurso.VEHICULO, 1L, 5L, null,
                "P.A. MARCO CANAVESES SOALHÕES", LocalDateTime.of(2026, 6, 5, 16, 23),
                new BigDecimal("52.30"), new BigDecimal("28.56"), new BigDecimal("1.961"),
                "GASOLEO", null, null, null);

        service.createOcrValidado(
                "campo1", request,
                "P.A. MARCO CANAVESES SOALHÕES", LocalDateTime.of(2026, 6, 5, 16, 23),
                new BigDecimal("52.30"), new BigDecimal("28.56"), new BigDecimal("1.961"),
                "GASOLEO", null);

        ArgumentCaptor<Ticket> captor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getNumTarjeta4ultimos()).isEqualTo("9012");
    }

    @Test
    @DisplayName("escalarTicketsAntiguos escala a INCIDENCIA los tickets PENDIENTE/SIN_COINCIDENCIA con mas de 30 dias desde la fecha de la operacion")
    void escalarTicketsAntiguos_escalaLosSuficientementeAntiguos() {
        Ticket viejo = Ticket.builder()
                .estadoCotejo("PENDIENTE")
                .fechaHora(LocalDateTime.now().minusDays(35))
                .importeTotal(new BigDecimal("52.30"))
                .build();
        Ticket reciente = Ticket.builder()
                .estadoCotejo("SIN_COINCIDENCIA")
                .fechaHora(LocalDateTime.now().minusDays(10))
                .importeTotal(new BigDecimal("40.00"))
                .build();

        when(ticketRepository.findByEstadoCotejoIn(List.of("PENDIENTE", "SIN_COINCIDENCIA")))
                .thenReturn(List.of(viejo, reciente));
        when(usuarioRepository.findAll()).thenReturn(List.of());

        int escalados = service.escalarTicketsAntiguos();

        assertThat(escalados).isEqualTo(1);
        assertThat(viejo.getEstadoCotejo()).isEqualTo("INCIDENCIA");
        assertThat(viejo.getTipoIncidencia()).isEqualTo("SIN_COTEJAR_1_MES");
        assertThat(reciente.getEstadoCotejo()).isEqualTo("SIN_COINCIDENCIA");
    }

    @Test
    @DisplayName("eliminar hace borrado logico (softDelete), no borra la fila")
    void eliminar_hacesBorradoLogico() {
        Ticket ticket = Ticket.builder()
                .estadoCotejo("PENDIENTE")
                .importeTotal(new BigDecimal("52.30"))
                .build();
        ticket.setId(10L);

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        service.eliminar(10L);

        assertThat(ticket.isEliminado()).isTrue();
        assertThat(ticket.getEliminadoEn()).isNotNull();
        verify(ticketRepository).save(ticket);
    }

    @Test
    @DisplayName("eliminar sobre un ticket inexistente lanza ResourceNotFoundException")
    void eliminar_ticketInexistente_lanzaExcepcion() {
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(999L))
                .isInstanceOf(com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("eliminarMasivo hace borrado logico de todos los tickets indicados")
    void eliminarMasivo_marcaVariosTicketsComoEliminados() {
        Ticket t1 = Ticket.builder().estadoCotejo("PENDIENTE").importeTotal(new BigDecimal("10.00")).build();
        t1.setId(1L);
        Ticket t2 = Ticket.builder().estadoCotejo("PENDIENTE").importeTotal(new BigDecimal("20.00")).build();
        t2.setId(2L);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(t1));
        when(ticketRepository.findById(2L)).thenReturn(Optional.of(t2));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        service.eliminarMasivo(List.of(1L, 2L));

        assertThat(t1.isEliminado()).isTrue();
        assertThat(t2.isEliminado()).isTrue();
        verify(ticketRepository).save(t1);
        verify(ticketRepository).save(t2);
    }

    @Test
    @DisplayName("eliminarMasivo con un id inexistente lanza ResourceNotFoundException")
    void eliminarMasivo_idInexistente_lanzaExcepcion() {
        Ticket t1 = Ticket.builder().estadoCotejo("PENDIENTE").importeTotal(new BigDecimal("10.00")).build();
        t1.setId(1L);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(t1));
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminarMasivo(List.of(1L, 999L)))
                .isInstanceOf(com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("cotejarPendientes vincula la operacion aunque el importe no coincida (tarjetas de flota facturan a precio pactado, distinto del ticket fisico) — bug real: antes quedaba en SIN_COINCIDENCIA")
    void cotejarPendientes_vinculaOperacionConImporteDistinto() {
        Ticket ticket = Ticket.builder()
                .estadoCotejo("PENDIENTE")
                .fechaHora(LocalDateTime.of(2026, 7, 1, 10, 4))
                .importeTotal(new BigDecimal("74.00"))
                .litros(new BigDecimal("44.90"))
                .numTarjeta4ultimos("5614")
                .build();

        Operacion operacion = Operacion.builder()
                .fechaHora(LocalDateTime.of(2026, 7, 1, 10, 4))
                .importeTotal(new BigDecimal("63.27"))
                .cantidad(new BigDecimal("44.90"))
                .establecimiento("E.S. GEVORA")
                .build();

        when(ticketRepository.findByEstadoCotejo("PENDIENTE")).thenReturn(List.of(ticket));
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(operacionRepository.findParaCotejoConTarjeta(any(), any(), eq("5614")))
                .thenReturn(List.of(operacion));

        service.cotejarPendientes();

        assertThat(ticket.getOperacionCotejada()).isEqualTo(operacion);
        assertThat(ticket.getEstadoCotejo()).isEqualTo("INCIDENCIA");
        assertThat(ticket.getTipoIncidencia()).isEqualTo("PRECIO_NO_CONCUERDA");
    }

    @Test
    @DisplayName("cotejarPendientes desambigua entre varias operaciones de la misma tarjeta eligiendo la mas cercana en importe (diesel + AdBlue repostados casi a la vez)")
    void cotejarPendientes_desambiguaPorImporteCercano() {
        Ticket ticket = Ticket.builder()
                .estadoCotejo("PENDIENTE")
                .fechaHora(LocalDateTime.of(2026, 7, 1, 10, 4))
                .importeTotal(new BigDecimal("74.00"))
                .numTarjeta4ultimos("5614")
                .build();

        Operacion diesel = Operacion.builder()
                .fechaHora(LocalDateTime.of(2026, 7, 1, 10, 4))
                .importeTotal(new BigDecimal("63.27"))
                .build();
        Operacion adblue = Operacion.builder()
                .fechaHora(LocalDateTime.of(2026, 7, 1, 10, 5))
                .importeTotal(new BigDecimal("20.70"))
                .build();

        when(ticketRepository.findByEstadoCotejo("PENDIENTE")).thenReturn(List.of(ticket));
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(operacionRepository.findParaCotejoConTarjeta(any(), any(), eq("5614")))
                .thenReturn(List.of(adblue, diesel));

        service.cotejarPendientes();

        assertThat(ticket.getOperacionCotejada()).isEqualTo(diesel);
    }

    @Test
    @DisplayName("cotejarPendientes marca MULTIPLE cuando dos operaciones quedan igual de cerca en importe (ambiguedad real, no se arriesga a cotejar mal)")
    void cotejarPendientes_variasCandidatasEmpatadas_marcaMultiple() {
        Ticket ticket = Ticket.builder()
                .estadoCotejo("PENDIENTE")
                .fechaHora(LocalDateTime.of(2026, 7, 1, 10, 4))
                .importeTotal(new BigDecimal("50.00"))
                .numTarjeta4ultimos("5614")
                .build();

        Operacion op1 = Operacion.builder()
                .fechaHora(LocalDateTime.of(2026, 7, 1, 10, 4))
                .importeTotal(new BigDecimal("45.00"))
                .build();
        Operacion op2 = Operacion.builder()
                .fechaHora(LocalDateTime.of(2026, 7, 1, 10, 6))
                .importeTotal(new BigDecimal("55.00"))
                .build();

        when(ticketRepository.findByEstadoCotejo("PENDIENTE")).thenReturn(List.of(ticket));
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(operacionRepository.findParaCotejoConTarjeta(any(), any(), eq("5614")))
                .thenReturn(List.of(op1, op2));

        service.cotejarPendientes();

        assertThat(ticket.getEstadoCotejo()).isEqualTo("MULTIPLE");
        assertThat(ticket.getOperacionCotejada()).isNull();
    }

    @Test
    @DisplayName("createManual lanza BusinessException si ya existe un ticket con la misma tarjeta, fecha e importe (foto repetida de la misma compra)")
    void createManual_duplicado_lanzaExcepcion() {
        CreateTicketManualRequest request = new CreateTicketManualRequest(
                null, null, null, null,
                "E.S. ESPINOSA", LocalDateTime.of(2026, 7, 21, 12, 18),
                new BigDecimal("12.02"), null, null, null, null, null,
                "9309", null, null, null);

        when(ticketRepository.findPosiblesDuplicados(eq("9309"), any(), any(), eq(new BigDecimal("12.02"))))
                .thenReturn(List.of(new Ticket()));

        assertThatThrownBy(() -> service.createManual(request))
                .isInstanceOf(com.tecozam.bills.shared.infrastructure.exception.BusinessException.class);

        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    @DisplayName("createManual sin tarjeta conocida no comprueba duplicados (no hay señal fuerte para distinguir compras distintas)")
    void createManual_sinTarjeta_noComprubaDuplicados() {
        CreateTicketManualRequest request = new CreateTicketManualRequest(
                null, null, null, null,
                "E.S. ESPINOSA", LocalDateTime.of(2026, 7, 21, 12, 18),
                new BigDecimal("12.02"), null, null, null, null, null,
                null, null, null, null);

        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createManual(request);

        verify(ticketRepository, never()).findPosiblesDuplicados(any(), any(), any(), any());
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    @DisplayName("createOcrValidado lanza BusinessException si ya existe un ticket duplicado (misma tarjeta, fecha e importe)")
    void createOcrValidado_duplicado_lanzaExcepcion() {
        UsuarioCampo campo = new UsuarioCampo();
        campo.setUsername("campo1");
        campo.setTrabajador(trabajador);
        when(usuarioCampoRepository.findByUsername("campo1")).thenReturn(Optional.of(campo));

        when(tarjetaRepository.findById(200L)).thenReturn(Optional.of(tarjeta));
        when(tarjetaAsignacionRepository.findActivaByTarjetaIdAndTrabajadorId(eq(200L), eq(2L), any(LocalDate.class)))
                .thenReturn(Optional.of(new TarjetaAsignacion()));
        when(vehiculoRepository.findById(1L)).thenReturn(Optional.of(vehiculo));
        when(ticketRepository.findPosiblesDuplicados(eq("9012"), any(), any(), eq(new BigDecimal("52.30"))))
                .thenReturn(List.of(new Ticket()));

        CreateTicketOcrValidadoRequest request = new CreateTicketOcrValidadoRequest(
                200L, null, CategoriaRecurso.VEHICULO, 1L, 5L, null,
                "P.A. MARCO CANAVESES SOALHÕES", LocalDateTime.of(2026, 6, 5, 16, 23),
                new BigDecimal("52.30"), new BigDecimal("28.56"), new BigDecimal("1.961"),
                "GASOLEO", null, null, null);

        assertThatThrownBy(() -> service.createOcrValidado(
                "campo1", request,
                "P.A. MARCO CANAVESES SOALHÕES", LocalDateTime.of(2026, 6, 5, 16, 23),
                new BigDecimal("52.30"), new BigDecimal("28.56"), new BigDecimal("1.961"),
                "GASOLEO", null))
                .isInstanceOf(com.tecozam.bills.shared.infrastructure.exception.BusinessException.class);

        verify(ticketRepository, never()).save(any(Ticket.class));
    }
}
