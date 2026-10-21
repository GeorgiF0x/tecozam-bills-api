package com.tecozam.bills.admin.application;

import com.tecozam.bills.factura.domain.TarjetaResumen;
import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import com.tecozam.bills.factura.infrastructure.persistence.TarjetaResumenRepository;
import com.tecozam.bills.shared.domain.enums.ConceptoUnificado;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import com.tecozam.bills.viat.domain.Viat;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReclasificarTarjetasAViatServiceTest {

    @Mock OperacionRepository operacionRepository;
    @Mock TarjetaRepository tarjetaRepository;
    @Mock TarjetaResumenRepository tarjetaResumenRepository;
    @Mock TarjetaAsignacionRepository tarjetaAsignacionRepository;
    @Mock TicketRepository ticketRepository;
    @Mock ViatRepository viatRepository;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) EntityManager em;

    ReclasificarTarjetasAViatService service;

    @BeforeEach
    void setUp() {
        service = new ReclasificarTarjetasAViatService(operacionRepository, tarjetaRepository,
                tarjetaResumenRepository, tarjetaAsignacionRepository, ticketRepository, viatRepository, em);
    }

    private Tarjeta tarjeta(Long id, String numero) {
        Tarjeta tarjeta = Tarjeta.builder().numeroTarjeta(numero).alias("alias-" + numero).activa(true).build();
        tarjeta.setId(id);
        return tarjeta;
    }

    @Test
    @DisplayName("3+ operaciones y 100% peaje, sin tickets ni asignaciones: reclasifica TARJETA->VIAT, desvincula TarjetaResumen y audita")
    void reclasifica_3OperacionesTodasPeaje_sinReferenciasDirectas_mueveAViat() {
        Tarjeta tarjeta = tarjeta(10L, "903338001814200008");
        when(operacionRepository.countByTarjetaResumen_Tarjeta_Id(10L)).thenReturn(3L);
        when(operacionRepository.countByTarjetaResumen_Tarjeta_IdAndConceptoUnificadoNot(10L,
                ConceptoUnificado.PEAJE.name())).thenReturn(0L);
        when(ticketRepository.existsByTarjetaId(10L)).thenReturn(false);
        when(tarjetaAsignacionRepository.existsByTarjetaId(10L)).thenReturn(false);
        when(viatRepository.findByCodigo("903338001814200008")).thenReturn(Optional.empty());
        when(viatRepository.save(org.mockito.ArgumentMatchers.any(Viat.class)))
                .thenAnswer(inv -> {
                    Viat v = inv.getArgument(0);
                    v.setId(99L);
                    return v;
                });
        TarjetaResumen tr = TarjetaResumen.builder().id(1L).numTarjeta("903338001814200008").tarjeta(tarjeta).build();
        when(tarjetaResumenRepository.findByTarjetaId(10L)).thenReturn(List.of(tr));

        ReclasificarTarjetasAViatResponse response =
                service.reclasificarTarjetasTocadas(List.of(tarjeta), false, "test");

        assertThat(response.dryRun()).isFalse();
        assertThat(response.candidatos()).isEqualTo(1);
        assertThat(response.movidas()).isEqualTo(1);
        assertThat(response.protegidas()).isEqualTo(0);
        assertThat(response.numerosMovidos()).containsExactly("903338001814200008");

        assertThat(tr.getTarjeta()).isNull();
        verify(tarjetaResumenRepository).saveAll(List.of(tr));
        verify(tarjetaRepository).delete(tarjeta);
        verify(em).createNativeQuery(anyString());
    }

    @Test
    @DisplayName("solo 2 operaciones de peaje (bajo el umbral de 3): NO reclasifica")
    void noReclasifica_soloDosOperaciones() {
        Tarjeta tarjeta = tarjeta(11L, "111");
        when(operacionRepository.countByTarjetaResumen_Tarjeta_Id(11L)).thenReturn(2L);

        ReclasificarTarjetasAViatResponse response =
                service.reclasificarTarjetasTocadas(List.of(tarjeta), false, "test");

        assertThat(response.candidatos()).isEqualTo(0);
        assertThat(response.movidas()).isEqualTo(0);
        verify(tarjetaRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("3 operaciones pero con alguna de combustible (no 100% peaje): NO reclasifica")
    void noReclasifica_tresOperacionesConCombustible() {
        Tarjeta tarjeta = tarjeta(12L, "222");
        when(operacionRepository.countByTarjetaResumen_Tarjeta_Id(12L)).thenReturn(3L);
        when(operacionRepository.countByTarjetaResumen_Tarjeta_IdAndConceptoUnificadoNot(12L,
                ConceptoUnificado.PEAJE.name())).thenReturn(1L);

        ReclasificarTarjetasAViatResponse response =
                service.reclasificarTarjetasTocadas(List.of(tarjeta), false, "test");

        assertThat(response.candidatos()).isEqualTo(0);
        assertThat(response.movidas()).isEqualTo(0);
        verify(tarjetaRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("cumple el umbral pero tiene tickets vinculados por FK directa: se protege, NO se borra la tarjeta")
    void noReclasifica_tieneTicketsVinculados_quedaProtegida() {
        Tarjeta tarjeta = tarjeta(13L, "333");
        when(operacionRepository.countByTarjetaResumen_Tarjeta_Id(13L)).thenReturn(5L);
        when(operacionRepository.countByTarjetaResumen_Tarjeta_IdAndConceptoUnificadoNot(13L,
                ConceptoUnificado.PEAJE.name())).thenReturn(0L);
        when(ticketRepository.existsByTarjetaId(13L)).thenReturn(true);

        ReclasificarTarjetasAViatResponse response =
                service.reclasificarTarjetasTocadas(List.of(tarjeta), false, "test");

        assertThat(response.candidatos()).isEqualTo(1);
        assertThat(response.movidas()).isEqualTo(0);
        assertThat(response.protegidas()).isEqualTo(1);
        assertThat(response.numerosProtegidos()).containsExactly("333");
        verify(tarjetaRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(viatRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("cumple el umbral pero tiene una asignación activa vinculada por FK directa: se protege, NO se borra la tarjeta")
    void noReclasifica_tieneAsignacionActiva_quedaProtegida() {
        Tarjeta tarjeta = tarjeta(14L, "444");
        when(operacionRepository.countByTarjetaResumen_Tarjeta_Id(14L)).thenReturn(4L);
        when(operacionRepository.countByTarjetaResumen_Tarjeta_IdAndConceptoUnificadoNot(14L,
                ConceptoUnificado.PEAJE.name())).thenReturn(0L);
        when(ticketRepository.existsByTarjetaId(14L)).thenReturn(false);
        when(tarjetaAsignacionRepository.existsByTarjetaId(14L)).thenReturn(true);

        ReclasificarTarjetasAViatResponse response =
                service.reclasificarTarjetasTocadas(List.of(tarjeta), false, "test");

        assertThat(response.movidas()).isEqualTo(0);
        assertThat(response.protegidas()).isEqualTo(1);
        verify(tarjetaRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("dryRun=true: detecta candidata pero no mueve nada (ni Viat, ni borrado, ni auditoría)")
    void dryRun_detectaCandidataSinMoverNada() {
        Tarjeta tarjeta = tarjeta(15L, "555");
        when(operacionRepository.countByTarjetaResumen_Tarjeta_Id(15L)).thenReturn(3L);
        when(operacionRepository.countByTarjetaResumen_Tarjeta_IdAndConceptoUnificadoNot(15L,
                ConceptoUnificado.PEAJE.name())).thenReturn(0L);
        when(ticketRepository.existsByTarjetaId(15L)).thenReturn(false);
        when(tarjetaAsignacionRepository.existsByTarjetaId(15L)).thenReturn(false);

        ReclasificarTarjetasAViatResponse response =
                service.reclasificarTarjetasTocadas(List.of(tarjeta), true, "test");

        assertThat(response.dryRun()).isTrue();
        assertThat(response.candidatos()).isEqualTo(1);
        assertThat(response.movidas()).isEqualTo(0);
        assertThat(response.numerosMovidos()).containsExactly("555");
        verify(tarjetaRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(viatRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(tarjetaResumenRepository, never()).findByTarjetaId(anyLong());
    }

    @Test
    @DisplayName("tarjetas duplicadas en la lista (mismo id) se deduplican, no se procesan dos veces")
    void deduplicaTarjetasPorId() {
        Tarjeta tarjeta = tarjeta(16L, "666");
        when(operacionRepository.countByTarjetaResumen_Tarjeta_Id(16L)).thenReturn(1L);

        ReclasificarTarjetasAViatResponse response =
                service.reclasificarTarjetasTocadas(List.of(tarjeta, tarjeta, tarjeta), false, "test");

        verify(operacionRepository, org.mockito.Mockito.times(1)).countByTarjetaResumen_Tarjeta_Id(16L);
        assertThat(response.candidatos()).isEqualTo(0);
    }
}
