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
                null, null, null, null, null, "TEST", null, PageRequest.of(0, 50));
        assertThat(sinFiltro.getContent())
                .extracting(Operacion::getEstablecimiento)
                .contains("ESTABLECIMIENTO LIBRE TEST", "ESTABLECIMIENTO VINCULADO TEST");

        var soloDisponibles = operacionRepository.findWithFiltersEager(
                null, null, null, null, null, "TEST", true, PageRequest.of(0, 50));
        assertThat(soloDisponibles.getContent())
                .extracting(Operacion::getEstablecimiento)
                .contains("ESTABLECIMIENTO LIBRE TEST")
                .doesNotContain("ESTABLECIMIENTO VINCULADO TEST");
    }
}
