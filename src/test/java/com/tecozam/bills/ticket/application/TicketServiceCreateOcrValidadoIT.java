package com.tecozam.bills.ticket.application;

import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.factura.domain.Factura;
import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.domain.TarjetaResumen;
import com.tecozam.bills.factura.infrastructure.persistence.FacturaRepository;
import com.tecozam.bills.factura.infrastructure.persistence.TarjetaResumenRepository;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.domain.TarjetaAsignacion;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.ticket.dto.CreateTicketOcrValidadoRequest;
import com.tecozam.bills.ticket.dto.LineaOcrDTO;
import com.tecozam.bills.ticket.dto.TicketDTO;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketLineaRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import com.tecozam.bills.vehiculo.domain.CategoriaRecurso;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import com.tecozam.bills.vehiculo.infrastructure.persistence.VehiculoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba end-to-end de T3 (odd/tasks/ticket-multi-foto.md): N fotos -> 1
 * ticket con N TicketLinea, agregados correctos en el Ticket, y las fotos
 * persistidas de verdad en disco (antes se descartaban tras el OCR).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TicketServiceCreateOcrValidadoIT {

    @Autowired private TicketService ticketService;
    @Autowired private TicketLineaRepository ticketLineaRepository;
    @Autowired private ProveedorRepository proveedorRepository;
    @Autowired private TarjetaRepository tarjetaRepository;
    @Autowired private TarjetaAsignacionRepository tarjetaAsignacionRepository;
    @Autowired private TrabajadorRepository trabajadorRepository;
    @Autowired private UsuarioCampoRepository usuarioCampoRepository;
    @Autowired private VehiculoRepository vehiculoRepository;
    @Autowired private FacturaRepository facturaRepository;
    @Autowired private TarjetaResumenRepository tarjetaResumenRepository;
    @Autowired private com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository operacionRepository;

    private final List<String> ficherosEscritos = new ArrayList<>();

    @AfterEach
    void borrarFicherosDePrueba() {
        // Los ficheros NO forman parte de la transaccion de BD (que se
        // deshace sola): hay que limpiarlos a mano.
        for (String ruta : ficherosEscritos) {
            try {
                Files.deleteIfExists(Path.of(ruta));
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    @DisplayName("createOcrValidado con 2 fotos crea 1 ticket con 2 TicketLinea, agregados correctos y fotos persistidas en disco")
    void crea1TicketConVariasLineasYFotosPersistidas() throws Exception {
        Proveedor proveedor = proveedorRepository.findByCodigo("REPSOL")
                .orElseGet(() -> proveedorRepository.save(Proveedor.builder()
                        .codigo("REPSOL").nombre("REPSOL").activo(true).build()));

        Tarjeta tarjeta = tarjetaRepository.save(Tarjeta.builder()
                .numeroTarjeta("9998887776665551")
                .alias("TARJETA TEST MULTIFOTO")
                .proveedor(proveedor)
                .build());

        Trabajador trabajador = trabajadorRepository.save(Trabajador.builder()
                .nombre("Test").apellidos("MultiFoto")
                .email("test-multifoto-" + System.nanoTime() + "@tecozam.test")
                .build());

        usuarioCampoRepository.save(UsuarioCampo.builder()
                .username("test-ocr-multifoto")
                .password("irrelevante")
                .trabajador(trabajador)
                .build());

        tarjetaAsignacionRepository.save(TarjetaAsignacion.builder()
                .tarjeta(tarjeta)
                .trabajador(trabajador)
                .fechaDesde(LocalDate.now().minusDays(1))
                .build());

        Vehiculo vehiculo = vehiculoRepository.save(Vehiculo.builder()
                .matricula("1234TMF")
                .tipo("TURISMO")
                .categoria(CategoriaRecurso.VEHICULO)
                .build());

        LocalDateTime fecha1 = LocalDateTime.now().minusHours(2);
        LocalDateTime fecha2 = LocalDateTime.now().minusHours(1);
        List<LineaOcrDTO> lineas = List.of(
                new LineaOcrDTO(0, "ESTACION TEST MULTIFOTO", fecha1, "GASOLEO", "REC-1", null, null,
                        new BigDecimal("20.00"), new BigDecimal("1.750"), new BigDecimal("35.00")),
                new LineaOcrDTO(1, "ESTACION TEST MULTIFOTO", fecha2, "GASOLEO", null, null, null,
                        new BigDecimal("10.00"), new BigDecimal("1.750"), new BigDecimal("17.50")));

        CreateTicketOcrValidadoRequest request = new CreateTicketOcrValidadoRequest(
                tarjeta.getId(), null, CategoriaRecurso.VEHICULO, vehiculo.getId(), 1L, 12345, null);

        List<MultipartFile> fotos = List.of(
                new MockMultipartFile("imagenes", "foto0.jpg", "image/jpeg", "foto-0-bytes".getBytes()),
                new MockMultipartFile("imagenes", "foto1.jpg", "image/jpeg", "foto-1-bytes".getBytes()));

        TicketDTO resultado = ticketService.createOcrValidado("test-ocr-multifoto", request, lineas, fotos);

        assertThat(resultado.estacion()).isEqualTo("ESTACION TEST MULTIFOTO");
        assertThat(resultado.importeTotal()).isEqualByComparingTo("52.50");
        assertThat(resultado.litros()).isEqualByComparingTo("30.00");
        assertThat(resultado.fechaHora()).isEqualTo(fecha1);

        var lineasGuardadas = ticketLineaRepository.findByTicketId(resultado.id());
        assertThat(lineasGuardadas).hasSize(2);
        assertThat(lineasGuardadas).allMatch(l -> l.getFotoUrl() != null && !l.getFotoUrl().isBlank());

        for (var linea : lineasGuardadas) {
            ficherosEscritos.add(linea.getFotoUrl());
            assertThat(Files.exists(Path.of(linea.getFotoUrl())))
                    .as("La foto de la linea con indice %d debe existir en disco", linea.getFotoIndice())
                    .isTrue();
        }
    }

    /**
     * T4 (odd/tasks/ticket-multi-foto.md): confirma que el rediseño de T1-T3
     * no rompió el anti-duplicado real. Antes de esta feature, N fotos de UN
     * ticket generaban N llamadas a createOcrValidado (una por foto), cada
     * una disparando verificarNoEsDuplicado por separado — con el riesgo de
     * que la 2a foto del MISMO ticket se confundiera con un duplicado. Ahora
     * las N fotos se validan JUNTAS en una sola llamada con un importeTotal
     * agregado, así que ya no hay forma de que eso ocurra (ver el otro test
     * de esta clase, que ya crea un ticket de 2 fotos sin que salte la
     * excepción). Lo que SÍ debe seguir bloqueado es el reenvío accidental
     * del ticket COMPLETO ya creado: dos llamadas a createOcrValidado con
     * los mismos datos agregados (misma tarjeta, misma fecha, mismo
     * importe).
     */
    @Test
    @DisplayName("createOcrValidado rechaza un segundo envío con los mismos datos agregados (reenvío accidental del mismo ticket)")
    void segundoEnvioDelMismoTicketEsRechazadoComoDuplicado() throws Exception {
        Proveedor proveedor = proveedorRepository.findByCodigo("REPSOL")
                .orElseGet(() -> proveedorRepository.save(Proveedor.builder()
                        .codigo("REPSOL").nombre("REPSOL").activo(true).build()));

        Tarjeta tarjeta = tarjetaRepository.save(Tarjeta.builder()
                .numeroTarjeta("9998887776665552")
                .alias("TARJETA TEST DUPLICADO")
                .proveedor(proveedor)
                .build());

        Trabajador trabajador = trabajadorRepository.save(Trabajador.builder()
                .nombre("Test").apellidos("Duplicado")
                .email("test-duplicado-" + System.nanoTime() + "@tecozam.test")
                .build());

        usuarioCampoRepository.save(UsuarioCampo.builder()
                .username("test-ocr-duplicado")
                .password("irrelevante")
                .trabajador(trabajador)
                .build());

        tarjetaAsignacionRepository.save(TarjetaAsignacion.builder()
                .tarjeta(tarjeta)
                .trabajador(trabajador)
                .fechaDesde(LocalDate.now().minusDays(1))
                .build());

        Vehiculo vehiculo = vehiculoRepository.save(Vehiculo.builder()
                .matricula("1234TDU")
                .tipo("TURISMO")
                .categoria(CategoriaRecurso.VEHICULO)
                .build());

        LocalDateTime fecha = LocalDateTime.now().minusHours(3);
        List<LineaOcrDTO> lineas = List.of(
                new LineaOcrDTO(0, "ESTACION TEST DUPLICADO", fecha, "GASOLEO", "REC-D1", null, null,
                        new BigDecimal("20.00"), new BigDecimal("1.750"), new BigDecimal("35.00")),
                new LineaOcrDTO(1, "ESTACION TEST DUPLICADO", fecha.plusMinutes(2), "GASOLEO", null, null, null,
                        new BigDecimal("10.00"), new BigDecimal("1.750"), new BigDecimal("17.50")));

        CreateTicketOcrValidadoRequest request = new CreateTicketOcrValidadoRequest(
                tarjeta.getId(), null, CategoriaRecurso.VEHICULO, vehiculo.getId(), 1L, 12345, null);

        // Primer envío: debe crearse sin problema (igual que el otro test).
        List<MultipartFile> fotos1 = List.of(
                new MockMultipartFile("imagenes", "foto0.jpg", "image/jpeg", "foto-dup-0".getBytes()),
                new MockMultipartFile("imagenes", "foto1.jpg", "image/jpeg", "foto-dup-1".getBytes()));
        TicketDTO primero = ticketService.createOcrValidado("test-ocr-duplicado", request, lineas, fotos1);
        var lineasGuardadas = ticketLineaRepository.findByTicketId(primero.id());
        lineasGuardadas.forEach(l -> ficherosEscritos.add(l.getFotoUrl()));

        // Segundo envío con los MISMOS datos agregados (misma tarjeta, fecha
        // más temprana e importe total): debe rechazarse como duplicado.
        List<MultipartFile> fotos2 = List.of(
                new MockMultipartFile("imagenes", "foto0.jpg", "image/jpeg", "foto-dup-0-reenvio".getBytes()),
                new MockMultipartFile("imagenes", "foto1.jpg", "image/jpeg", "foto-dup-1-reenvio".getBytes()));
        assertThatThrownBy(() ->
                ticketService.createOcrValidado("test-ocr-duplicado", request, lineas, fotos2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ya existe un ticket con la misma tarjeta, fecha e importe");
    }

    /**
     * T5 (odd/tasks/ticket-multi-foto.md): lineas con estaciones distintas
     * (posible mezcla de fotos de compras diferentes) no bloquean la
     * creacion del ticket, pero lo marcan para revision manual.
     */
    @Test
    @DisplayName("createOcrValidado con lineas de estaciones distintas se crea igualmente, marcado requiereRevisionManual")
    void lineasInconsistentesSeCreanMarcadasParaRevision() throws Exception {
        Proveedor proveedor = proveedorRepository.findByCodigo("REPSOL")
                .orElseGet(() -> proveedorRepository.save(Proveedor.builder()
                        .codigo("REPSOL").nombre("REPSOL").activo(true).build()));

        Tarjeta tarjeta = tarjetaRepository.save(Tarjeta.builder()
                .numeroTarjeta("9998887776665553")
                .alias("TARJETA TEST REVISION")
                .proveedor(proveedor)
                .build());

        Trabajador trabajador = trabajadorRepository.save(Trabajador.builder()
                .nombre("Test").apellidos("Revision")
                .email("test-revision-" + System.nanoTime() + "@tecozam.test")
                .build());

        usuarioCampoRepository.save(UsuarioCampo.builder()
                .username("test-ocr-revision")
                .password("irrelevante")
                .trabajador(trabajador)
                .build());

        tarjetaAsignacionRepository.save(TarjetaAsignacion.builder()
                .tarjeta(tarjeta)
                .trabajador(trabajador)
                .fechaDesde(LocalDate.now().minusDays(1))
                .build());

        Vehiculo vehiculo = vehiculoRepository.save(Vehiculo.builder()
                .matricula("1234TRV")
                .tipo("TURISMO")
                .categoria(CategoriaRecurso.VEHICULO)
                .build());

        LocalDateTime fecha = LocalDateTime.now().minusHours(4);
        List<LineaOcrDTO> lineas = List.of(
                new LineaOcrDTO(0, "ESTACION A", fecha, "GASOLEO", "REC-R1", null, null,
                        new BigDecimal("20.00"), new BigDecimal("1.750"), new BigDecimal("35.00")),
                new LineaOcrDTO(1, "ESTACION B", fecha.plusMinutes(2), "GASOLEO", null, null, null,
                        new BigDecimal("10.00"), new BigDecimal("1.750"), new BigDecimal("17.50")));

        CreateTicketOcrValidadoRequest request = new CreateTicketOcrValidadoRequest(
                tarjeta.getId(), null, CategoriaRecurso.VEHICULO, vehiculo.getId(), 1L, 12345, null);

        List<MultipartFile> fotos = List.of(
                new MockMultipartFile("imagenes", "foto0.jpg", "image/jpeg", "foto-rev-0".getBytes()),
                new MockMultipartFile("imagenes", "foto1.jpg", "image/jpeg", "foto-rev-1".getBytes()));

        TicketDTO resultado = ticketService.createOcrValidado("test-ocr-revision", request, lineas, fotos);
        ticketLineaRepository.findByTicketId(resultado.id()).forEach(l -> ficherosEscritos.add(l.getFotoUrl()));

        assertThat(resultado.requiereRevisionManual()).isTrue();
        assertThat(resultado.observaciones()).contains("Revisar:").contains("estaciones distintas");
    }

    /**
     * T6 (odd/tasks/ticket-multi-foto.md): confirma que el cotejo automatico
     * YA funciona correctamente para un ticket de varias lineas, SIN cambios
     * de codigo. El cotejo (tanto el intento inmediato de createOcrValidado
     * como el batch cotejarPendientes) solo lee los campos escalares
     * agregados de Ticket (importeTotal/fechaHora/litros) contra UNA
     * Operacion real — nunca lee `lineas`. Eso es correcto por diseno: N
     * fotos de un ticket siguen siendo UNA compra real = UNA Operacion en la
     * factura (p.ej. diesel repostado, partido en 2 fotos por ser un ticket
     * largo), no N operaciones distintas. El importeTotal agregado (suma de
     * lineas, ya calculado en T3) es exactamente el dato que hay que casar
     * contra el importe de esa unica Operacion real.
     */
    @Test
    @DisplayName("createOcrValidado con 2 lineas se cateja automaticamente contra la UNICA operacion real que corresponde a la compra completa")
    void ticketDeVariasLineasSeCotejaContraLaOperacionRealDelImporteAgregado() throws Exception {
        Proveedor proveedor = proveedorRepository.findByCodigo("REPSOL")
                .orElseGet(() -> proveedorRepository.save(Proveedor.builder()
                        .codigo("REPSOL").nombre("REPSOL").activo(true).build()));

        Tarjeta tarjeta = tarjetaRepository.save(Tarjeta.builder()
                .numeroTarjeta("9998887776665554")
                .alias("TARJETA TEST COTEJO MULTIFOTO")
                .proveedor(proveedor)
                .build());

        Trabajador trabajador = trabajadorRepository.save(Trabajador.builder()
                .nombre("Test").apellidos("CotejoMultifoto")
                .email("test-cotejo-multifoto-" + System.nanoTime() + "@tecozam.test")
                .build());

        usuarioCampoRepository.save(UsuarioCampo.builder()
                .username("test-ocr-cotejo-multifoto")
                .password("irrelevante")
                .trabajador(trabajador)
                .build());

        tarjetaAsignacionRepository.save(TarjetaAsignacion.builder()
                .tarjeta(tarjeta)
                .trabajador(trabajador)
                .fechaDesde(LocalDate.now().minusDays(1))
                .build());

        Vehiculo vehiculo = vehiculoRepository.save(Vehiculo.builder()
                .matricula("1234TCM")
                .tipo("TURISMO")
                .categoria(CategoriaRecurso.VEHICULO)
                .build());

        LocalDateTime fecha1 = LocalDateTime.now().minusHours(2);
        LocalDateTime fecha2 = LocalDateTime.now().minusHours(1).minusMinutes(50);
        // 2 fotos del MISMO ticket (misma compra): la suma de sus importes
        // (35.00 + 17.50 = 52.50) es lo que debe casar contra la operacion real.
        List<LineaOcrDTO> lineas = List.of(
                new LineaOcrDTO(0, "ESTACION TEST COTEJO", fecha1, "GASOLEO", "REC-C1", null, null,
                        new BigDecimal("20.00"), new BigDecimal("1.750"), new BigDecimal("35.00")),
                new LineaOcrDTO(1, "ESTACION TEST COTEJO", fecha2, "GASOLEO", null, null, null,
                        new BigDecimal("10.00"), new BigDecimal("1.750"), new BigDecimal("17.50")));

        // La Operacion real de la factura: UNA sola linea por la compra completa.
        Factura factura = facturaRepository.save(Factura.builder()
                .proveedor(proveedor)
                .numFactura("TEST-COTEJO-MULTIFOTO")
                .build());
        TarjetaResumen resumen = tarjetaResumenRepository.save(TarjetaResumen.builder()
                .factura(factura)
                .numTarjeta(tarjeta.getNumeroTarjeta())
                .build());
        operacionRepository.save(Operacion.builder()
                .tarjetaResumen(resumen)
                .factura(factura)
                .establecimiento("ESTACION TEST COTEJO")
                .conceptoUnificado("Gasoleo A")
                .fechaHora(fecha1)
                .importeTotal(new BigDecimal("52.50"))
                .cantidad(new BigDecimal("30.00"))
                .build());

        CreateTicketOcrValidadoRequest request = new CreateTicketOcrValidadoRequest(
                tarjeta.getId(), null, CategoriaRecurso.VEHICULO, vehiculo.getId(), 1L, 12345, null);

        List<MultipartFile> fotos = List.of(
                new MockMultipartFile("imagenes", "foto0.jpg", "image/jpeg", "foto-cotejo-0".getBytes()),
                new MockMultipartFile("imagenes", "foto1.jpg", "image/jpeg", "foto-cotejo-1".getBytes()));

        TicketDTO resultado = ticketService.createOcrValidado("test-ocr-cotejo-multifoto", request, lineas, fotos);
        ticketLineaRepository.findByTicketId(resultado.id()).forEach(l -> ficherosEscritos.add(l.getFotoUrl()));

        assertThat(resultado.estadoCotejo()).isEqualTo("COTEJADO");
        assertThat(resultado.operacionCotejadaId()).isNotNull();
    }

    /**
     * T6: igual que el test anterior, pero la Operacion real difiere en
     * importe mas de un 5% del agregado del ticket — debe seguir
     * detectandose como discrepancia (INCIDENCIA), exactamente igual que ya
     * pasaba antes de esta feature con un ticket de 1 sola linea. Prueba que
     * `detectarDiscrepancia` sigue funcionando sin cambios sobre el agregado.
     */
    @Test
    @DisplayName("createOcrValidado con 2 lineas detecta discrepancia de importe contra la operacion real, igual que con 1 linea")
    void ticketDeVariasLineasDetectaDiscrepanciaDeImporteIgualQueConUnaLinea() throws Exception {
        Proveedor proveedor = proveedorRepository.findByCodigo("REPSOL")
                .orElseGet(() -> proveedorRepository.save(Proveedor.builder()
                        .codigo("REPSOL").nombre("REPSOL").activo(true).build()));

        Tarjeta tarjeta = tarjetaRepository.save(Tarjeta.builder()
                .numeroTarjeta("9998887776665555")
                .alias("TARJETA TEST DISCREPANCIA MULTIFOTO")
                .proveedor(proveedor)
                .build());

        Trabajador trabajador = trabajadorRepository.save(Trabajador.builder()
                .nombre("Test").apellidos("DiscrepanciaMultifoto")
                .email("test-discrepancia-multifoto-" + System.nanoTime() + "@tecozam.test")
                .build());

        usuarioCampoRepository.save(UsuarioCampo.builder()
                .username("test-ocr-discrepancia-multifoto")
                .password("irrelevante")
                .trabajador(trabajador)
                .build());

        tarjetaAsignacionRepository.save(TarjetaAsignacion.builder()
                .tarjeta(tarjeta)
                .trabajador(trabajador)
                .fechaDesde(LocalDate.now().minusDays(1))
                .build());

        Vehiculo vehiculo = vehiculoRepository.save(Vehiculo.builder()
                .matricula("1234TDM")
                .tipo("TURISMO")
                .categoria(CategoriaRecurso.VEHICULO)
                .build());

        LocalDateTime fecha1 = LocalDateTime.now().minusHours(2);
        LocalDateTime fecha2 = LocalDateTime.now().minusHours(1).minusMinutes(50);
        // Agregado: 35.00 + 17.50 = 52.50
        List<LineaOcrDTO> lineas = List.of(
                new LineaOcrDTO(0, "ESTACION TEST DISCREPANCIA", fecha1, "GASOLEO", "REC-DI1", null, null,
                        new BigDecimal("20.00"), new BigDecimal("1.750"), new BigDecimal("35.00")),
                new LineaOcrDTO(1, "ESTACION TEST DISCREPANCIA", fecha2, "GASOLEO", null, null, null,
                        new BigDecimal("10.00"), new BigDecimal("1.750"), new BigDecimal("17.50")));

        // Operacion real con importe MUY distinto del agregado (>5%): 70.00 vs 52.50.
        Factura factura = facturaRepository.save(Factura.builder()
                .proveedor(proveedor)
                .numFactura("TEST-DISCREPANCIA-MULTIFOTO")
                .build());
        TarjetaResumen resumen = tarjetaResumenRepository.save(TarjetaResumen.builder()
                .factura(factura)
                .numTarjeta(tarjeta.getNumeroTarjeta())
                .build());
        operacionRepository.save(Operacion.builder()
                .tarjetaResumen(resumen)
                .factura(factura)
                .establecimiento("ESTACION TEST DISCREPANCIA")
                .conceptoUnificado("Gasoleo A")
                .fechaHora(fecha1)
                .importeTotal(new BigDecimal("70.00"))
                .build());

        CreateTicketOcrValidadoRequest request = new CreateTicketOcrValidadoRequest(
                tarjeta.getId(), null, CategoriaRecurso.VEHICULO, vehiculo.getId(), 1L, 12345, null);

        List<MultipartFile> fotos = List.of(
                new MockMultipartFile("imagenes", "foto0.jpg", "image/jpeg", "foto-discrepancia-0".getBytes()),
                new MockMultipartFile("imagenes", "foto1.jpg", "image/jpeg", "foto-discrepancia-1".getBytes()));

        TicketDTO resultado = ticketService.createOcrValidado(
                "test-ocr-discrepancia-multifoto", request, lineas, fotos);
        ticketLineaRepository.findByTicketId(resultado.id()).forEach(l -> ficherosEscritos.add(l.getFotoUrl()));

        assertThat(resultado.estadoCotejo()).isEqualTo("INCIDENCIA");
        assertThat(resultado.tipoIncidencia()).isEqualTo("PRECIO_NO_CONCUERDA");
    }
}
