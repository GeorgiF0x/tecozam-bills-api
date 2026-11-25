package com.tecozam.bills.factura.infrastructure.persistence;

import com.tecozam.bills.factura.domain.Factura;
import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.domain.TarjetaResumen;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.ticket.domain.Ticket;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug real reportado por el cliente (video 2026-09-29): el buscador de
 * "vincular operacion" (VincularDialog en tickets/page.tsx) podia seguir
 * ofreciendo una operacion ya vinculada a OTRO ticket, sin ningun aviso —
 * permitiendo (o al menos sugiriendo) vincular la misma operacion dos veces.
 * Causa raiz: OperacionRepository.findWithFiltersEager nunca excluia
 * operaciones ya referenciadas por Ticket.operacionCotejada.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OperacionRepositoryIT {

    @Autowired
    private OperacionRepository operacionRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private FacturaRepository facturaRepository;

    @Autowired
    private TarjetaResumenRepository tarjetaResumenRepository;

    @Autowired
    private ProveedorRepository proveedorRepository;

    @Test
    @DisplayName("soloDisponibles=true excluye una operacion ya vinculada a un ticket no eliminado")
    void soloDisponiblesExcluyeOperacionYaVinculada() {
        Proveedor proveedor = proveedorRepository.save(Proveedor.builder()
                .codigo("TEST-OPREPO-" + System.nanoTime())
                .nombre("Proveedor test OperacionRepositoryIT")
                .activo(true)
                .build());

        Factura factura = facturaRepository.save(Factura.builder()
                .proveedor(proveedor)
                .numFactura("TEST-OPREPO")
                .build());

        TarjetaResumen resumen = tarjetaResumenRepository.save(TarjetaResumen.builder()
                .factura(factura)
                .numTarjeta("9999")
                .build());

        Operacion operacionLibre = operacionRepository.save(Operacion.builder()
                .tarjetaResumen(resumen)
                .factura(factura)
                .establecimiento("ESTABLECIMIENTO LIBRE TEST")
                .conceptoUnificado("Gasoleo A")
                .fechaHora(LocalDateTime.now().minusDays(1))
                .importeTotal(new BigDecimal("30.00"))
                .build());

        Operacion operacionYaVinculada = operacionRepository.save(Operacion.builder()
                .tarjetaResumen(resumen)
                .factura(factura)
                .establecimiento("ESTABLECIMIENTO VINCULADO TEST")
                .conceptoUnificado("Gasoleo A")
                .fechaHora(LocalDateTime.now().minusDays(2))
                .importeTotal(new BigDecimal("40.00"))
                .build());

        ticketRepository.save(Ticket.builder()
                .origen("OCR")
                .estacion("ESTABLECIMIENTO VINCULADO TEST")
                .importeTotal(new BigDecimal("40.00"))
                .estadoCotejo("COTEJADO")
                .operacionCotejada(operacionYaVinculada)
                .build());

        var sinFiltro = operacionRepository.findWithFiltersEager(
                null, null, null, null, null, "TEST", null, null, null, null, null, PageRequest.of(0, 50));
        assertThat(sinFiltro.getContent())
                .extracting(Operacion::getEstablecimiento)
                .contains("ESTABLECIMIENTO LIBRE TEST", "ESTABLECIMIENTO VINCULADO TEST");

        var soloDisponibles = operacionRepository.findWithFiltersEager(
                null, null, null, null, null, "TEST", true, null, null, null, null, PageRequest.of(0, 50));
        assertThat(soloDisponibles.getContent())
                .extracting(Operacion::getEstablecimiento)
                .contains("ESTABLECIMIENTO LIBRE TEST")
                .doesNotContain("ESTABLECIMIENTO VINCULADO TEST");
    }

    // ── Buscador de "vincular operacion" (odd/tasks/paquete-cotejo-b.md, S4 y S5) ──

    private Proveedor proveedorBuscador;

    /**
     * Dos tarjetas: una con conductor y otra sin (conductor NULL, para probar
     * que la busqueda de texto no rompe con nulos). Tres operaciones con
     * importes y litros distintos para los rangos.
     */
    private void crearDatosBuscador() {
        proveedorBuscador = proveedorRepository.save(Proveedor.builder()
                .codigo("TEST-BUSCADOR-" + System.nanoTime())
                .nombre("Proveedor test buscador")
                .activo(true)
                .build());
        Factura factura = facturaRepository.save(Factura.builder()
                .proveedor(proveedorBuscador)
                .numFactura("TEST-BUSCADOR")
                .build());
        TarjetaResumen conConductor = tarjetaResumenRepository.save(TarjetaResumen.builder()
                .factura(factura)
                .numTarjeta("708011008022412025")
                .conductor("Conductor Buscador Uno")
                .build());
        TarjetaResumen sinConductor = tarjetaResumenRepository.save(TarjetaResumen.builder()
                .factura(factura)
                .numTarjeta("708011008099990000")
                .build());
        operacionRepository.save(Operacion.builder()
                .tarjetaResumen(conConductor).factura(factura)
                .establecimiento("BUSCADOR OP-50").referencia("REF-BUSC-001")
                .fechaHora(LocalDateTime.now().minusDays(1))
                .importeTotal(new BigDecimal("50.00")).cantidad(new BigDecimal("30.00"))
                .build());
        operacionRepository.save(Operacion.builder()
                .tarjetaResumen(conConductor).factura(factura)
                .establecimiento("BUSCADOR OP-107").referencia("REF-BUSC-002")
                .fechaHora(LocalDateTime.now().minusDays(2))
                .importeTotal(new BigDecimal("107.00")).cantidad(new BigDecimal("53.39"))
                .build());
        operacionRepository.save(Operacion.builder()
                .tarjetaResumen(sinConductor).factura(factura)
                .establecimiento("BUSCADOR OP-200").referencia("REF-BUSC-003")
                .fechaHora(LocalDateTime.now().minusDays(3))
                .importeTotal(new BigDecimal("200.00")).cantidad(new BigDecimal("100.00"))
                .build());
    }

    private java.util.List<String> buscar(String q, BigDecimal importeMin, BigDecimal importeMax,
                                          BigDecimal litrosMin, BigDecimal litrosMax) {
        return operacionRepository.findWithFiltersEager(
                        proveedorBuscador.getId(), null, null, null, null, q, null,
                        importeMin, importeMax, litrosMin, litrosMax, PageRequest.of(0, 50))
                .getContent().stream().map(Operacion::getEstablecimiento).toList();
    }

    @Test
    @DisplayName("importeMin/importeMax filtran por importeTotal, con los limites incluidos")
    void filtraPorRangoDeImporte() {
        crearDatosBuscador();

        assertThat(buscar(null, new BigDecimal("50.00"), new BigDecimal("107.00"), null, null))
                .containsExactlyInAnyOrder("BUSCADOR OP-50", "BUSCADOR OP-107");
        assertThat(buscar(null, new BigDecimal("107.00"), null, null, null))
                .containsExactlyInAnyOrder("BUSCADOR OP-107", "BUSCADOR OP-200");
    }

    @Test
    @DisplayName("litrosMin/litrosMax filtran por cantidad, con los limites incluidos")
    void filtraPorRangoDeLitros() {
        crearDatosBuscador();

        assertThat(buscar(null, null, null, new BigDecimal("53.39"), new BigDecimal("100.00")))
                .containsExactlyInAnyOrder("BUSCADOR OP-107", "BUSCADOR OP-200");
        assertThat(buscar(null, null, null, null, new BigDecimal("53.39")))
                .containsExactlyInAnyOrder("BUSCADOR OP-50", "BUSCADOR OP-107");
    }

    @Test
    @DisplayName("q busca tambien en numero de tarjeta, conductor (sin distinguir mayusculas) y referencia, sin romper con conductor nulo")
    void qBuscaEnTarjetaConductorYReferencia() {
        crearDatosBuscador();

        assertThat(buscar("22412025", null, null, null, null))
                .containsExactlyInAnyOrder("BUSCADOR OP-50", "BUSCADOR OP-107");
        assertThat(buscar("conductor buscador", null, null, null, null))
                .containsExactlyInAnyOrder("BUSCADOR OP-50", "BUSCADOR OP-107");
        assertThat(buscar("ref-busc-003", null, null, null, null))
                .containsExactly("BUSCADOR OP-200");
        // La busqueda previa por establecimiento sigue funcionando
        assertThat(buscar("op-107", null, null, null, null))
                .containsExactly("BUSCADOR OP-107");
    }
}
