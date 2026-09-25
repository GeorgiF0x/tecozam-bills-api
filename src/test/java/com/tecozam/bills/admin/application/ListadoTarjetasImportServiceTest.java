package com.tecozam.bills.admin.application;

import com.tecozam.bills.admin.dto.ConfiguracionImportDTO;
import com.tecozam.bills.admin.dto.ImportTarjetasReportDTO;
import com.tecozam.bills.admin.infrastructure.import_.FilaImportada;
import com.tecozam.bills.admin.infrastructure.import_.ListadoTarjetasParserFactory;
import com.tecozam.bills.admin.infrastructure.import_.LlmListadoTarjetasParser;
import com.tecozam.bills.admin.infrastructure.import_.TipoRecurso;
import com.tecozam.bills.centrocoste.infrastructure.persistence.CentroCosteRepository;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.trabajador.application.TrabajadorResolver;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios (Mockito) del modo LLM de {@link ListadoTarjetasImportService}
 * (ver odd/tasks/import-llm-switch.md). El modo regex ya está cubierto por
 * {@link ListadoTarjetasImportServiceIT} (integración, con Excel real) — aquí
 * solo se prueba el branch nuevo, con {@link LlmListadoTarjetasParser} mockeado.
 */
@ExtendWith(MockitoExtension.class)
class ListadoTarjetasImportServiceTest {

    @Mock ListadoTarjetasParserFactory parserFactory;
    @Mock ProveedorRepository proveedorRepo;
    @Mock TarjetaRepository tarjetaRepo;
    @Mock ViatRepository viatRepo;
    @Mock TrabajadorRepository trabajadorRepo;
    @Mock TrabajadorResolver trabajadorResolver;
    @Mock CentroCosteRepository centroCosteRepo;
    @Mock ConfiguracionImportService configuracionImportService;
    @Mock LlmListadoTarjetasParser llmListadoTarjetasParser;

    ListadoTarjetasImportService service;
    Proveedor proveedor;

    @BeforeEach
    void setUp() {
        service = new ListadoTarjetasImportService(parserFactory, proveedorRepo, tarjetaRepo, viatRepo,
                trabajadorRepo, trabajadorResolver, centroCosteRepo, configuracionImportService,
                llmListadoTarjetasParser);

        proveedor = new Proveedor();
        proveedor.setId(1L);
        proveedor.setCodigo("REPSOL");
        proveedor.setNombre("Repsol");
        proveedor.setActivo(true);

        when(configuracionImportService.obtener())
                .thenReturn(new ConfiguracionImportDTO(true, null, null));
        when(proveedorRepo.findByCodigo("REPSOL")).thenReturn(Optional.of(proveedor));
    }

    @Test
    @DisplayName("modo LLM: fila con clasificacion LLM/regex coincidente se materializa")
    void modoLlm_clasificacionCoincide_seMaterializa() throws Exception {
        FilaImportada fila = new FilaImportada(
                "0007078833651671188", "1234-ABC", "Juan Perez", "OBRA-1", "DIESEL E+", TipoRecurso.TARJETA, true);
        when(llmListadoTarjetasParser.parsearHoja(any(), eq("REPSOL"))).thenReturn(List.of(fila));
        when(tarjetaRepo.findByNumeroTarjeta(any())).thenReturn(Optional.empty());
        when(tarjetaRepo.save(any(Tarjeta.class))).thenAnswer(inv -> inv.getArgument(0));

        ImportTarjetasReportDTO report = service.importar(excelVacio(), "REPSOL");

        verify(tarjetaRepo).save(any(Tarjeta.class));
        assertThat(report.tarjetasCreadas()).isEqualTo(1);
        assertThat(report.filasParaRevision()).isEmpty();
    }

    @Test
    @DisplayName("modo LLM: fila con clasificacion LLM/regex discrepante NO se materializa y va a revision")
    void modoLlm_clasificacionDiscrepa_noMaterializaYQuedaParaRevision() throws Exception {
        // El regex clasificaria esto como TARJETA (no contiene keywords VIAT),
        // pero el LLM sugiere VIAT -> discrepancia.
        FilaImportada fila = new FilaImportada(
                "0007078833651671188", "1234-ABC", "Juan Perez", "OBRA-1", "DIESEL E+", TipoRecurso.VIAT, true);
        when(llmListadoTarjetasParser.parsearHoja(any(), eq("REPSOL"))).thenReturn(List.of(fila));

        ImportTarjetasReportDTO report = service.importar(excelVacio(), "REPSOL");

        verify(tarjetaRepo, never()).save(any(Tarjeta.class));
        verify(viatRepo, never()).save(any());
        assertThat(report.tarjetasCreadas()).isZero();
        assertThat(report.viatsCreados()).isZero();
        assertThat(report.filasParaRevision()).hasSize(1);
        assertThat(report.filasParaRevision().get(0)).contains("0007078833651671188");
    }

    private static MultipartFile excelVacio() throws Exception {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            wb.createSheet("Hoja1");
            wb.write(baos);
            return new MockMultipartFile(
                    "file", "listado.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baos.toByteArray());
        }
    }
}
