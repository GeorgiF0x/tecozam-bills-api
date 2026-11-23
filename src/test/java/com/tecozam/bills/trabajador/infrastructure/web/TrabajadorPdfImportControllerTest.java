package com.tecozam.bills.trabajador.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecozam.bills.shared.dto.ConfirmarImportPdfRequest;
import com.tecozam.bills.shared.dto.FilaPreviewDTO;
import com.tecozam.bills.shared.infrastructure.llm.LlmListaExtractorService;
import com.tecozam.bills.trabajador.application.TrabajadorImportService;
import com.tecozam.bills.trabajador.dto.ImportTrabajadoresReportDTO;
import com.tecozam.bills.trabajador.infrastructure.import_.TrabajadorXlsxRowParser;
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
 * Unit test del import PDF+IA de trabajadores (ver
 * odd/tasks/maestros-import-pdf-ia.md T2). Solo prueba la lógica de mapeo +
 * validación ({@code mapearYValidar}, package-private) y de confirmación
 * ({@code confirmarFilas}, package-private) con un {@link TrabajadorXlsxRowParser}
 * real (función pura, sin BD) — nunca llama a la IA real.
 */
@ExtendWith(MockitoExtension.class)
class TrabajadorPdfImportControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private LlmListaExtractorService llmListaExtractorService;
    @Mock
    private TrabajadorImportService trabajadorImportService;

    private TrabajadorPdfImportController controller;

    @BeforeEach
    void setUp() {
        controller = new TrabajadorPdfImportController(
                new TrabajadorXlsxRowParser(), llmListaExtractorService, trabajadorImportService);
    }

    private ObjectNode filaCruda(String nombre, String apellidos, String email, String dniNie, String origen) {
        ObjectNode nodo = MAPPER.createObjectNode();
        ponerOTexto(nodo, "nombre", nombre);
        ponerOTexto(nodo, "apellidos", apellidos);
        ponerOTexto(nodo, "email", email);
        ponerOTexto(nodo, "dniNie", dniNie);
        ponerOTexto(nodo, "origen", origen);
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
        List<JsonNode> crudas = List.of(filaCruda("Juan", "Garcia Lopez", "juan@test.com", "12345678A", "CAMPO"));

        List<FilaPreviewDTO> filas = controller.mapearYValidar(crudas);

        assertThat(filas).hasSize(1);
        FilaPreviewDTO fila = filas.get(0);
        assertThat(fila.valida()).isTrue();
        assertThat(fila.error()).isNull();
        assertThat(fila.campos())
                .containsEntry("nombre", "Juan")
                .containsEntry("apellidos", "Garcia Lopez")
                .containsEntry("email", "juan@test.com")
                .containsEntry("dniNie", "12345678A")
                .containsEntry("origen", "CAMPO");
    }

    @Test
    @DisplayName("mapearYValidar: fila sin apellidos sale invalida con el mensaje esperado")
    void filaSinApellidos() {
        List<JsonNode> crudas = List.of(filaCruda("Ana", null, null, null, null));

        List<FilaPreviewDTO> filas = controller.mapearYValidar(crudas);

        assertThat(filas).hasSize(1);
        FilaPreviewDTO fila = filas.get(0);
        assertThat(fila.valida()).isFalse();
        assertThat(fila.error()).isEqualTo("Nombre y apellidos son obligatorios");
        assertThat(fila.campos()).containsEntry("nombre", "Ana").containsEntry("apellidos", "");
    }

    @Test
    @DisplayName("mapearYValidar: origen ausente o invalido cae a IMPORTACION por defecto")
    void origenPorDefecto() {
        List<JsonNode> crudas = List.of(
                filaCruda("Ana", "Perez", null, null, null),
                filaCruda("Luis", "Ruiz", null, null, "NO_EXISTE"));

        List<FilaPreviewDTO> filas = controller.mapearYValidar(crudas);

        assertThat(filas).hasSize(2);
        assertThat(filas.get(0).campos()).containsEntry("origen", "IMPORTACION");
        assertThat(filas.get(0).valida()).isTrue();
        assertThat(filas.get(1).campos()).containsEntry("origen", "IMPORTACION");
        assertThat(filas.get(1).valida()).isTrue();
    }

    @Test
    @DisplayName("mapearYValidar: fila completamente vacia se omite del resultado")
    void filaVaciaSeOmite() {
        List<JsonNode> crudas = List.of(
                filaCruda(null, null, null, null, null),
                filaCruda("Juan", "Garcia", null, null, null));

        List<FilaPreviewDTO> filas = controller.mapearYValidar(crudas);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).campos()).containsEntry("nombre", "Juan");
    }

    private Map<String, String> filaConfirmada(String nombre, String apellidos, String email, String dniNie,
                                                String origen) {
        Map<String, String> campos = new LinkedHashMap<>();
        if (nombre != null) campos.put("nombre", nombre);
        if (apellidos != null) campos.put("apellidos", apellidos);
        if (email != null) campos.put("email", email);
        if (dniNie != null) campos.put("dniNie", dniNie);
        if (origen != null) campos.put("origen", origen);
        return campos;
    }

    @Test
    @DisplayName("confirmarFilas: cuenta creados/existentes/errores correctamente")
    void confirmarFilasCuentaBien() {
        when(trabajadorImportService.procesarFilaValidada(any())).thenReturn(false, true);

        ConfirmarImportPdfRequest request = new ConfirmarImportPdfRequest(List.of(
                filaConfirmada("Juan", "Garcia", "juan@test.com", "12345678A", "CAMPO"),
                filaConfirmada("Ana", "Perez", "ana@test.com", null, "OFICINA")));

        ImportTrabajadoresReportDTO reporte = controller.confirmarFilas(request);

        assertThat(reporte.creados()).isEqualTo(1);
        assertThat(reporte.existentes()).isEqualTo(1);
        assertThat(reporte.errores()).isEmpty();
    }

    @Test
    @DisplayName("confirmarFilas: fila invalida tras edicion se cuenta como error, no se persiste")
    void confirmarFilasConFilaInvalida() {
        ConfirmarImportPdfRequest request = new ConfirmarImportPdfRequest(List.of(
                filaConfirmada("Solo Nombre", null, null, null, null)));

        ImportTrabajadoresReportDTO reporte = controller.confirmarFilas(request);

        assertThat(reporte.creados()).isZero();
        assertThat(reporte.existentes()).isZero();
        assertThat(reporte.errores()).hasSize(1);
        assertThat(reporte.errores().get(0)).contains("Solo Nombre");
    }
}
