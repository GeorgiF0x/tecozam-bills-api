package com.tecozam.bills.ticket.application;

import com.tecozam.bills.factura.domain.Factura;
import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.domain.TarjetaResumen;
import com.tecozam.bills.factura.infrastructure.persistence.FacturaRepository;
import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import com.tecozam.bills.factura.infrastructure.persistence.TarjetaResumenRepository;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.ticket.domain.Ticket;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * odd/tasks/paquete-cotejo-b.md, S1: los PENDIENTE y los reintentados se
 * cotejan en la misma transaccion. La exclusividad (una operacion, un ticket)
 * depende de que Hibernate vuelque el ticket recien vinculado antes de cada
 * findParaCotejo* (su NOT EXISTS sobre Ticket). Se comprueba contra el SQL
 * Server real; @Transactional deshace todos los datos al terminar.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketServiceCotejoExclusividadIT {

    @Autowired private TicketService ticketService;
    @Autowired private TicketRepository ticketRepository;
    @Autowired private OperacionRepository operacionRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private TarjetaResumenRepository tarjetaResumenRepository;
    @Autowired private TarjetaRepository tarjetaRepository;
    @Autowired private ProveedorRepository proveedorRepository;

    @Test
    @DisplayName("un PENDIENTE y un SIN_COINCIDENCIA de la misma tarjeta y ventana con UNA sola operacion: solo uno queda vinculado")
    void unaOperacionSoloSeVinculaAUnTicketEnLaMismaPasada() {
        // Fecha lejana y tarjeta unica: ningun dato de desarrollo compite por la operacion
        LocalDateTime fecha = LocalDateTime.of(2031, 3, 10, 10, 0);
        String numero = "70801199" + String.format("%010d", System.nanoTime() % 10_000_000_000L);

        Proveedor proveedor = proveedorRepository.save(Proveedor.builder()
                .codigo("TEST-EXCL-" + System.nanoTime()).nombre("Proveedor test exclusividad").activo(true).build());
        Tarjeta tarjeta = tarjetaRepository.save(Tarjeta.builder()
                .numeroTarjeta(numero).alias("TARJETA TEST EXCLUSIVIDAD").proveedor(proveedor).build());
        Factura factura = facturaRepository.save(Factura.builder()
                .proveedor(proveedor).numFactura("TEST-EXCL").build());
        TarjetaResumen resumen = tarjetaResumenRepository.save(TarjetaResumen.builder()
                .factura(factura).numTarjeta(numero).tarjeta(tarjeta).build());
        Operacion operacion = operacionRepository.save(Operacion.builder()
                .tarjetaResumen(resumen).factura(factura)
                .establecimiento("ESTACION TEST EXCLUSIVIDAD")
                .fechaHora(fecha)
                .importeTotal(new BigDecimal("40.00")).cantidad(new BigDecimal("20.00"))
                .build());

        Ticket pendiente = ticketRepository.save(ticket("PENDIENTE", tarjeta, fecha.plusMinutes(5)));
        Ticket sinCoincidencia = ticketRepository.save(ticket("SIN_COINCIDENCIA", tarjeta, fecha.plusMinutes(10)));

        ticketService.cotejarPendientes();

        assertThat(Stream.of(pendiente, sinCoincidencia).filter(t -> t.getOperacionCotejada() != null))
                .hasSize(1);
        assertThat(ticketRepository.findByOperacionCotejadaIdActivos(operacion.getId())).hasSize(1);
    }

    private Ticket ticket(String estado, Tarjeta tarjeta, LocalDateTime fechaHora) {
        return Ticket.builder()
                .origen("MANUAL")
                .estacion("ESTACION TEST EXCLUSIVIDAD")
                .fechaHora(fechaHora)
                .importeTotal(new BigDecimal("40.00"))
                .litros(new BigDecimal("20.00"))
                .tarjeta(tarjeta)
                .estadoCotejo(estado)
                .build();
    }
}
