package com.tecozam.bills.factura.application;

import com.tecozam.bills.admin.application.ConfiguracionImportService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    @Mock FacturaParser facturaParser;

    FacturaService service;
    Proveedor proveedor;

    @BeforeEach
    void setUp() {
        service = new FacturaService(facturaRepository, proveedorRepository, tarjetaRepository,
                tarjetaAsignacionRepository, parserFactory, fileStorageService, ticketService,
                facturaImportValidator, configuracionImportService);

        proveedor = new Proveedor();
        proveedor.setId(1L);
        proveedor.setCodigo("REPSOL");
        proveedor.setNombre("Repsol");

        when(proveedorRepository.findById(1L)).thenReturn(Optional.of(proveedor));
        when(parserFactory.getParser(anyString(), any(Boolean.class))).thenReturn(facturaParser);
        when(facturaRepository.save(any(Factura.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tarjetaRepository.findByNumeroTarjeta(anyString())).thenReturn(Optional.empty());
        when(tarjetaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
}
