package com.tecozam.bills.viat.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecozam.bills.shared.dto.ConfirmarImportPdfRequest;
import com.tecozam.bills.shared.dto.FilaPreviewDTO;
import com.tecozam.bills.shared.infrastructure.llm.LlmListaExtractorService;
import com.tecozam.bills.viat.application.ViatImportService;
import com.tecozam.bills.viat.dto.ImportViatsReportDTO;
import com.tecozam.bills.viat.infrastructure.import_.ViatXlsxRowParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit test del import PDF+IA de VIATs (ver
 * odd/tasks/maestros-import-pdf-ia.md T5). Solo prueba la lógica de mapeo +
 * validación ({@code mapearYValidar}, package-private) y de confirmación
 * ({@code confirmar}, package-private por ser llamado directo en el test)
 * con un {@link ViatXlsxRowParser} real (función pura, sin BD) — nunca llama
 * a la IA real.
 */
@ExtendWith(MockitoExtension.class)
class ViatPdfImportControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private LlmListaExtractorService llmListaExtractorService;
    @Mock
    private ViatImportService viatImportService;

    private ViatPdfImportController controller;

    @BeforeEach
    void setUp() {
        controller = new ViatPdfImportController(
                new ViatXlsxRowParser(), llmListaExtractorService, viatImportService);
    }

    private ObjectNode filaCruda(String codigo, String numeroSerie, String descripcion) {
        ObjectNode nodo = MAPPER.createObjectNode();
        ponerOTexto(nodo, "codigo", codigo);
        ponerOTexto(nodo, "numeroSerie", numeroSerie);
        ponerOTexto(nodo, "descripcion", descripcion);
        return nodo;
    }

    private void ponerOTexto(ObjectNode nodo, String campo, String valor) {
        if (valor == null) {
            nodo.putNull(campo);
        } else {
            nodo.put(campo, valor);
        }
    }

    @Test
    @DisplayName("mapearYValidar: fila completa valida se mapea tal cual")
    void filaCompletaValida() {
        List<JsonNode> crudas = List.of(filaCruda("VIAT-001", "SN-123", "Dispositivo entrada norte"));

        List<FilaPreviewDTO> filas = controller.mapearYValidar(crudas);

        assertThat(filas).hasSize(1);
        FilaPreviewDTO fila = filas.get(0);
        assertThat(fila.valida()).isTrue();
        assertThat(fila.error()).isNull();
        assertThat(fila.campos())
                .containsEntry("codigo", "VIAT-001")
                .containsEntry("numeroSerie", "SN-123")
                .containsEntry("descripcion", "Dispositivo entrada norte");
    }

    @Test
    @DisplayName("mapearYValidar: fila sin codigo sale invalida con el mensaje esperado")
    void filaSinCodigo() {
        List<JsonNode> crudas = List.of(filaCruda(null, "SN-999", "Sin codigo"));

        List<FilaPreviewDTO> filas = controller.mapearYValidar(crudas);

        assertThat(filas).hasSize(1);
        FilaPreviewDTO fila = filas.get(0);
        assertThat(fila.valida()).isFalse();
        assertThat(fila.error()).isEqualTo("La columna codigo es obligatoria");
        assertThat(fila.campos()).containsEntry("codigo", "").containsEntry("numeroSerie", "SN-999");
    }

    @Test
    @DisplayName("mapearYValidar: fila completamente vacia se omite del resultado")
    void filaVaciaSeOmite() {
        List<JsonNode> crudas = List.of(
                filaCruda(null, null, null),
                filaCruda("VIAT-002", null, null));

        List<FilaPreviewDTO> filas = controller.mapearYValidar(crudas);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).campos()).containsEntry("codigo", "VIAT-002");
    }

    private Map<String, String> filaConfirmada(String codigo, String numeroSerie, String descripcion) {
        Map<String, String> campos = new LinkedHashMap<>();
        if (codigo != null) campos.put("codigo", codigo);
        if (numeroSerie != null) campos.put("numeroSerie", numeroSerie);
        if (descripcion != null) campos.put("descripcion", descripcion);
        return campos;
    }

    @Test
    @DisplayName("confirmar: cuenta creados/existentes correctamente (fila nueva y fila existente)")
    void confirmarCuentaBien() {
        when(viatImportService.procesarFilaValidada(any())).thenReturn(false, true);

        ConfirmarImportPdfRequest request = new ConfirmarImportPdfRequest(List.of(
                filaConfirmada("VIAT-NUEVO", "SN-1", "Nuevo dispositivo"),
                filaConfirmada("VIAT-EXISTENTE", "SN-2", "Ya existia")));

        ImportViatsReportDTO reporte = controller.confirmar(request).getBody();

        assertThat(reporte).isNotNull();
        assertThat(reporte.creados()).isEqualTo(1);
        assertThat(reporte.existentes()).isEqualTo(1);
        assertThat(reporte.errores()).isEmpty();
    }

    @Test
    @DisplayName("confirmar: fila invalida tras edicion se cuenta como error, no se persiste")
    void confirmarFilaInvalida() {
        ConfirmarImportPdfRequest request = new ConfirmarImportPdfRequest(List.of(
                filaConfirmada(null, "SN-3", "Sin codigo")));

        ImportViatsReportDTO reporte = controller.confirmar(request).getBody();

        assertThat(reporte).isNotNull();
        assertThat(reporte.creados()).isZero();
        assertThat(reporte.existentes()).isZero();
        assertThat(reporte.errores()).hasSize(1);
        assertThat(reporte.errores().get(0)).contains("Fila sin codigo");
    }
}
