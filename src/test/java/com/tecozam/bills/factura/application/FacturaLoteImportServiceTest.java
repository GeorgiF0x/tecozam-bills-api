package com.tecozam.bills.factura.application;

import com.tecozam.bills.factura.dto.ImportarFacturaLoteItemDTO;
import com.tecozam.bills.factura.dto.ImportarFacturaLoteResultadoDTO;
import com.tecozam.bills.factura.dto.ImportarFacturaResponse;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacturaLoteImportServiceTest {

    @Mock FacturaService facturaService;

    FacturaLoteImportService service;

    @BeforeEach
    void setUp() {
        service = new FacturaLoteImportService(facturaService);
    }

    @Test
    @DisplayName("lote con 2 items que importan bien, devuelve un resultado exitoso por cada uno en orden")
    void importarLote_dosItemsExitosos_devuelveDosResultadosExitosos() {
        MultipartFile factura0 = new MockMultipartFile("factura_0", "f0.pdf", "application/pdf", new byte[]{1});
        MultipartFile factura1 = new MockMultipartFile("factura_1", "f1.pdf", "application/pdf", new byte[]{1});
        Map<String, MultipartFile> fileMap = Map.of("factura_0", factura0, "factura_1", factura1);

        List<ImportarFacturaLoteItemDTO> items = List.of(
                new ImportarFacturaLoteItemDTO(1L, "factura_0", null),
                new ImportarFacturaLoteItemDTO(2L, "factura_1", null)
        );

        when(facturaService.importar(eq(1L), eq(factura0), eq(null)))
                .thenReturn(new ImportarFacturaResponse(10L, "FA-001", "Repsol", 1, 1, "/ruta/f0.pdf", "ok", List.of()));
        when(facturaService.importar(eq(2L), eq(factura1), eq(null)))
                .thenReturn(new ImportarFacturaResponse(11L, "FA-002", "Moeve", 1, 1, "/ruta/f1.pdf", "ok", List.of()));

        List<ImportarFacturaLoteResultadoDTO> resultados = service.importarLote(items, fileMap);

        assertThat(resultados).hasSize(2);
        assertThat(resultados.get(0)).isEqualTo(new ImportarFacturaLoteResultadoDTO(0, true, 10L, "FA-001", null));
        assertThat(resultados.get(1)).isEqualTo(new ImportarFacturaLoteResultadoDTO(1, true, 11L, "FA-002", null));
    }

    @Test
    @DisplayName("lote con 1 exito + 1 fallo, el fallo no aborta el resto del lote")
    void importarLote_unExitoYUnFallo_elFalloNoAbortaElResto() {
        MultipartFile facturaOk = new MockMultipartFile("factura_0", "f0.pdf", "application/pdf", new byte[]{1});
        MultipartFile facturaMala = new MockMultipartFile("factura_1", "f1.pdf", "application/pdf", new byte[]{1});
        Map<String, MultipartFile> fileMap = Map.of("factura_0", facturaOk, "factura_1", facturaMala);

        List<ImportarFacturaLoteItemDTO> items = List.of(
                new ImportarFacturaLoteItemDTO(1L, "factura_0", null),
                new ImportarFacturaLoteItemDTO(999L, "factura_1", null)
        );

        when(facturaService.importar(eq(1L), eq(facturaOk), eq(null)))
                .thenReturn(new ImportarFacturaResponse(10L, "FA-001", "Repsol", 1, 1, "/ruta/f0.pdf", "ok", List.of()));
        when(facturaService.importar(eq(999L), eq(facturaMala), eq(null)))
                .thenThrow(new ResourceNotFoundException("Proveedor", 999L));

        List<ImportarFacturaLoteResultadoDTO> resultados = service.importarLote(items, fileMap);

        assertThat(resultados).hasSize(2);
        assertThat(resultados.get(0).exito()).isTrue();
        assertThat(resultados.get(0).facturaId()).isEqualTo(10L);
        assertThat(resultados.get(1).exito()).isFalse();
        assertThat(resultados.get(1).facturaId()).isNull();
        assertThat(resultados.get(1).error()).isNotBlank();
    }

    @Test
    @DisplayName("un extractoKey null no intenta resolver extracto en el fileMap")
    void importarLote_extractoKeyNull_noLlamaConExtracto() {
        MultipartFile factura0 = new MockMultipartFile("factura_0", "f0.pdf", "application/pdf", new byte[]{1});
        Map<String, MultipartFile> fileMap = Map.of("factura_0", factura0);
        List<ImportarFacturaLoteItemDTO> items = List.of(new ImportarFacturaLoteItemDTO(1L, "factura_0", null));

        when(facturaService.importar(eq(1L), eq(factura0), any()))
                .thenReturn(new ImportarFacturaResponse(10L, "FA-001", "Repsol", 1, 1, "/ruta/f0.pdf", "ok", List.of()));

        List<ImportarFacturaLoteResultadoDTO> resultados = service.importarLote(items, fileMap);

        assertThat(resultados.get(0).exito()).isTrue();
    }
}
