package com.tecozam.bills.factura.application;

import com.tecozam.bills.admin.application.ConfiguracionImportService;
import com.tecozam.bills.admin.application.ReclasificarTarjetasAViatResponse;
import com.tecozam.bills.admin.application.ReclasificarTarjetasAViatService;
import com.tecozam.bills.admin.dto.ConfiguracionImportDTO;
import com.tecozam.bills.factura.domain.Factura;
import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.domain.TarjetaResumen;
import com.tecozam.bills.factura.dto.ImportarFacturaResponse;
import com.tecozam.bills.factura.infrastructure.parser.FacturaParseResult;
import com.tecozam.bills.factura.infrastructure.parser.FacturaParser;
import com.tecozam.bills.factura.infrastructure.parser.FacturaParserFactory;
import com.tecozam.bills.factura.infrastructure.persistence.FacturaRepository;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.domain.enums.ConceptoUnificado;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.shared.infrastructure.storage.FileStorageService;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.ticket.application.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacturaServiceTest {

    @Mock FacturaRepository facturaRepository;
    @Mock ProveedorRepository proveedorRepository;
    @Mock TarjetaRepository tarjetaRepository;
    @Mock TarjetaAsignacionRepository tarjetaAsignacionRepository;
    @Mock FacturaParserFactory parserFactory;
    @Mock FileStorageService fileStorageService;
    @Mock TicketService ticketService;
    @Mock FacturaImportValidator facturaImportValidator;
    @Mock ConfiguracionImportService configuracionImportService;
    @Mock ReclasificarTarjetasAViatService reclasificarTarjetasAViatService;
    @Mock FacturaParser facturaParser;

    FacturaService service;
    Proveedor proveedor;

    @BeforeEach
    void setUp() {
        service = new FacturaService(facturaRepository, proveedorRepository, tarjetaRepository,
                tarjetaAsignacionRepository, parserFactory, fileStorageService, ticketService,
                facturaImportValidator, configuracionImportService, reclasificarTarjetasAViatService);

        proveedor = new Proveedor();
        proveedor.setId(1L);
        proveedor.setCodigo("REPSOL");
        proveedor.setNombre("Repsol");

        org.mockito.Mockito.lenient().when(proveedorRepository.findById(1L)).thenReturn(Optional.of(proveedor));
        org.mockito.Mockito.lenient().when(parserFactory.getParser(anyString(), any(Boolean.class))).thenReturn(facturaParser);
        org.mockito.Mockito.lenient().when(facturaRepository.save(any(Factura.class))).thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient().when(tarjetaRepository.findByNumeroTarjeta(anyString())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(tarjetaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("con modo LLM activo y avisos de plausibilidad, marca la factura para revision manual")
    void importar_modoLlmActivoConAvisos_marcaRequiereRevisionManual() throws Exception {
        when(configuracionImportService.obtener())
                .thenReturn(new ConfiguracionImportDTO(true, null, null));
        when(facturaParser.parse(any(), any())).thenReturn(facturaParseResultConUnaOperacion());
        when(facturaImportValidator.validar(any(Factura.class)))
                .thenReturn(List.of("Total de factura no cuadra (aviso simulado)"));

        ImportarFacturaResponse response = service.importar(1L, pdfFile(), null);

        ArgumentCaptor<Factura> captor = ArgumentCaptor.forClass(Factura.class);
        org.mockito.Mockito.verify(facturaRepository).save(captor.capture());
        assertThat(captor.getValue().isRequiereRevisionManual()).isTrue();
        assertThat(response.avisos()).hasSize(1);
    }

    @Test
    @DisplayName("con modo regex (por defecto) y avisos de plausibilidad, NO marca revision manual — comportamiento sin cambios")
    void importar_modoRegexConAvisos_noMarcaRequiereRevisionManual() throws Exception {
        when(configuracionImportService.obtener())
                .thenReturn(new ConfiguracionImportDTO(false, null, null));
        when(facturaParser.parse(any(), any())).thenReturn(facturaParseResultConUnaOperacion());
        when(facturaImportValidator.validar(any(Factura.class)))
                .thenReturn(List.of("aviso informativo, no bloqueante"));

        service.importar(1L, pdfFile(), null);

        ArgumentCaptor<Factura> captor = ArgumentCaptor.forClass(Factura.class);
        org.mockito.Mockito.verify(facturaRepository).save(captor.capture());
        assertThat(captor.getValue().isRequiereRevisionManual()).isFalse();
    }

    @Test
    @DisplayName("sin avisos de plausibilidad, no marca revision manual aunque el modo LLM este activo")
    void importar_modoLlmActivoSinAvisos_noMarcaRequiereRevisionManual() throws Exception {
        when(configuracionImportService.obtener())
                .thenReturn(new ConfiguracionImportDTO(true, null, null));
        when(facturaParser.parse(any(), any())).thenReturn(facturaParseResultConUnaOperacion());
        when(facturaImportValidator.validar(any(Factura.class))).thenReturn(List.of());

        service.importar(1L, pdfFile(), null);

        ArgumentCaptor<Factura> captor = ArgumentCaptor.forClass(Factura.class);
        org.mockito.Mockito.verify(facturaRepository).save(captor.capture());
        assertThat(captor.getValue().isRequiereRevisionManual()).isFalse();
    }

    @Test
    @DisplayName("eliminarMasivo elimina todas las facturas indicadas")
    void eliminarMasivo_eliminaVariasFacturas() {
        Factura f1 = Factura.builder().id(10L).numFactura("FA-001").build();
        Factura f2 = Factura.builder().id(20L).numFactura("FA-002").build();

        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f1));
        when(facturaRepository.findById(20L)).thenReturn(Optional.of(f2));
        when(ticketService.desvincularPorFactura(anyLong())).thenReturn(0);

        service.eliminarMasivo(List.of(10L, 20L));

        verify(facturaRepository).delete(f1);
        verify(facturaRepository).delete(f2);
    }

    @Test
    @DisplayName("eliminarMasivo con un id inexistente lanza ResourceNotFoundException (no hace catch silencioso, la transaccion de clase revierte el lote)")
    void eliminarMasivo_idInexistente_lanzaExcepcion() {
        Factura f1 = Factura.builder().id(10L).numFactura("FA-001").build();

        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f1));
        when(facturaRepository.findById(999L)).thenReturn(Optional.empty());
        when(ticketService.desvincularPorFactura(anyLong())).thenReturn(0);

        assertThatThrownBy(() -> service.eliminarMasivo(List.of(10L, 999L)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("eliminarMasivo con un id duplicado en el lote lo procesa una sola vez")
    void eliminarMasivo_idDuplicado_procesaUnaSolaVez() {
        Factura f1 = Factura.builder().id(10L).numFactura("FA-001").build();

        when(facturaRepository.findById(10L)).thenReturn(Optional.of(f1));
        when(ticketService.desvincularPorFactura(anyLong())).thenReturn(0);

        service.eliminarMasivo(List.of(10L, 10L));

        verify(facturaRepository, org.mockito.Mockito.times(1)).delete(f1);
    }

    @Test
    @DisplayName("tras importar una factura CEPSA/MOEVE, invoca la reclasificación automática TARJETA->VIAT con las tarjetas tocadas (dryRun=false)")
    void importar_cepsaMoeve_invocaReclasificacionAutomatica() throws Exception {
        Proveedor cepsa = new Proveedor();
        cepsa.setId(2L);
        cepsa.setCodigo("MOEVE_CEPSA");
        cepsa.setNombre("Moeve/Cepsa");
        when(proveedorRepository.findById(2L)).thenReturn(Optional.of(cepsa));

        when(configuracionImportService.obtener()).thenReturn(new ConfiguracionImportDTO(false, null, null));
        when(facturaParser.parse(any(), any())).thenReturn(facturaParseResultConUnaOperacion());
        when(facturaImportValidator.validar(any(Factura.class))).thenReturn(List.of());
        when(reclasificarTarjetasAViatService.reclasificarTarjetasTocadas(any(), anyBoolean(), anyString()))
                .thenReturn(new ReclasificarTarjetasAViatResponse(false, 0, 0, 0, List.of(), List.of()));

        service.importar(2L, pdfFile(), extractoFile());

        org.mockito.Mockito.verify(reclasificarTarjetasAViatService)
                .reclasificarTarjetasTocadas(any(), eq(false), anyString());
    }

    @Test
    @DisplayName("tras importar una factura REPSOL, NO invoca la reclasificación TARJETA->VIAT (fuera de alcance, solo Cepsa/Moeve)")
    void importar_repsol_noInvocaReclasificacionAutomatica() throws Exception {
        when(configuracionImportService.obtener()).thenReturn(new ConfiguracionImportDTO(false, null, null));
        when(facturaParser.parse(any(), any())).thenReturn(facturaParseResultConUnaOperacion());
        when(facturaImportValidator.validar(any(Factura.class))).thenReturn(List.of());

        service.importar(1L, pdfFile(), null);

        org.mockito.Mockito.verifyNoInteractions(reclasificarTarjetasAViatService);
    }

    @Test
    @DisplayName("si la tarjeta facturada ya existe pero esta soft-deleted, la restaura antes de vincularla "
            + "(mismo bug ya corregido en ListadoTarjetasImportService: 'ya existia' sin restore() la deja "
            + "invisible en los listados pese a estar vinculada a la factura)")
    void importar_tarjetaEncontradaSoftDeleted_laRestaura() throws Exception {
        com.tecozam.bills.tarjeta.domain.Tarjeta tarjetaEliminada = com.tecozam.bills.tarjeta.domain.Tarjeta.builder()
                .numeroTarjeta("1234567890123456")
                .proveedor(proveedor)
                .build();
        tarjetaEliminada.setId(99L);
        tarjetaEliminada.softDelete();
        assertThat(tarjetaEliminada.isEliminado()).isTrue();

        when(tarjetaRepository.findByNumeroTarjeta(anyString())).thenReturn(Optional.of(tarjetaEliminada));
        when(configuracionImportService.obtener()).thenReturn(new ConfiguracionImportDTO(false, null, null));
        when(facturaParser.parse(any(), any())).thenReturn(facturaParseResultConUnaOperacion());
        when(facturaImportValidator.validar(any(Factura.class))).thenReturn(List.of());

        service.importar(1L, pdfFile(), null);

        assertThat(tarjetaEliminada.isEliminado()).isFalse();
        verify(tarjetaRepository).save(tarjetaEliminada);
    }

    private FacturaParseResult facturaParseResultConUnaOperacion() {
        Factura factura = Factura.builder()
                .numFactura("FA-TEST-001")
                .totalFactura(new BigDecimal("74.00"))
                .build();
        TarjetaResumen tr = TarjetaResumen.builder()
                .numTarjeta("1234567890123456")
                .conductor("Conductor de prueba")
                .operaciones(new java.util.ArrayList<>())
                .build();
        Operacion op = Operacion.builder()
                .fechaHora(LocalDateTime.now())
                .conceptoUnificado(ConceptoUnificado.DIESEL.name())
                .importeTotal(new BigDecimal("74.00"))
                .build();
        tr.getOperaciones().add(op);
        factura.getTarjetaResumenes().add(tr);
        return FacturaParseResult.builder()
                .factura(factura)
                .conceptos(List.of())
                .tarjetaResumenes(List.of(tr))
                .build();
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile("facturaPdf", "factura.pdf", "application/pdf", "contenido".getBytes());
    }

    private MockMultipartFile extractoFile() {
        return new MockMultipartFile("extractoPdf", "extracto.pdf", "application/pdf", "contenido".getBytes());
    }
}
