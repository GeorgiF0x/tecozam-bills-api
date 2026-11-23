package com.tecozam.bills.vehiculo.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecozam.bills.shared.dto.ConfirmarImportPdfRequest;
import com.tecozam.bills.shared.dto.FilaPreviewDTO;
import com.tecozam.bills.shared.infrastructure.llm.LlmListaExtractorService;
import com.tecozam.bills.vehiculo.application.VehiculoImportService;
import com.tecozam.bills.vehiculo.dto.ImportVehiculosReportDTO;
import com.tecozam.bills.vehiculo.infrastructure.import_.FilaVehiculoImportada;
import com.tecozam.bills.vehiculo.infrastructure.import_.VehiculoXlsxRowParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehiculoPdfImportControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock LlmListaExtractorService extractorService;
    @Mock VehiculoImportService vehiculoImportService;

    VehiculoPdfImportController controller;

    @BeforeEach
    void setUp() {
        // VehiculoXlsxRowParser.validar(...) es logica pura sin dependencias
        // de BD: se usa la instancia real, no un mock, igual que hace
        // VehiculoImportService internamente.
        controller = new VehiculoPdfImportController(extractorService, vehiculoImportService, new VehiculoXlsxRowParser());
    }

    private ObjectNode fila(String tipo, String matricula, String codigoObra, String categoria, String descripcion) {
        ObjectNode node = MAPPER.createObjectNode();
        putNullable(node, "tipo", tipo);
        putNullable(node, "matricula", matricula);
        putNullable(node, "codigoObra", codigoObra);
        putNullable(node, "categoria", categoria);
        putNullable(node, "descripcion", descripcion);
        return node;
    }

    private void putNullable(ObjectNode node, String campo, String valor) {
        if (valor == null) {
            node.putNull(campo);
        } else {
            node.put(campo, valor);
        }
    }

    @Test
    @DisplayName("Fila VEHICULO completa con matricula es valida")
    void filaVehiculoCompletaValida() {
        JsonNode cruda = fila("Furgoneta", "1234ABC", null, "VEHICULO", "Furgoneta de reparto");

        List<FilaPreviewDTO> resultado = controller.mapearYValidar(List.of(cruda));

        assertThat(resultado).hasSize(1);
        FilaPreviewDTO previa = resultado.get(0);
        assertThat(previa.valida()).isTrue();
        assertThat(previa.error()).isNull();
        assertThat(previa.campos())
                .containsEntry("tipo", "Furgoneta")
                .containsEntry("matricula", "1234ABC")
                .containsEntry("categoria", "VEHICULO")
                .containsEntry("descripcion", "Furgoneta de reparto");
    }

    @Test
    @DisplayName("Fila VEHICULO sin matricula sale invalida")
    void filaVehiculoSinMatriculaInvalida() {
        JsonNode cruda = fila("Turismo", null, null, "VEHICULO", null);

        List<FilaPreviewDTO> resultado = controller.mapearYValidar(List.of(cruda));

        assertThat(resultado).hasSize(1);
        FilaPreviewDTO previa = resultado.get(0);
        assertThat(previa.valida()).isFalse();
        assertThat(previa.error()).contains("matricula");
    }

    @Test
    @DisplayName("Fila INDUSTRIAL_MAQUINARIA con codigo de obra y sin matricula es valida")
    void filaIndustrialMaquinariaValida() {
        JsonNode cruda = fila("Excavadora", null, "OBRA-001", "INDUSTRIAL_MAQUINARIA", "Excavadora de oruga");

        List<FilaPreviewDTO> resultado = controller.mapearYValidar(List.of(cruda));

        assertThat(resultado).hasSize(1);
        FilaPreviewDTO previa = resultado.get(0);
        assertThat(previa.valida()).isTrue();
        assertThat(previa.campos())
                .containsEntry("codigoObra", "OBRA-001")
                .containsEntry("categoria", "INDUSTRIAL_MAQUINARIA");
    }

    @Test
    @DisplayName("Categoria invalida sale con el mensaje de categoria invalida")
    void categoriaInvalida() {
        JsonNode cruda = fila("Turismo", "2222BBB", null, "CAMION", null);

        List<FilaPreviewDTO> resultado = controller.mapearYValidar(List.of(cruda));

        assertThat(resultado).hasSize(1);
        FilaPreviewDTO previa = resultado.get(0);
        assertThat(previa.valida()).isFalse();
        assertThat(previa.error()).contains("Categoria").contains("CAMION").contains("invalida");
    }

    @Test
    @DisplayName("Fila completamente vacia se omite de la previsualizacion")
    void filaVaciaSeOmite() {
        JsonNode vacia = fila(null, null, null, null, null);
        JsonNode valida = fila("Turismo", "3333CCC", null, "VEHICULO", null);

        List<FilaPreviewDTO> resultado = controller.mapearYValidar(List.of(vacia, valida));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).campos()).containsEntry("matricula", "3333CCC");
    }

    @Test
    @DisplayName("Confirmar cuenta correctamente filas nuevas y ya existentes")
    void confirmarCuentaCreadosYExistentes() {
        Map<String, String> filaNueva = new HashMap<>();
        filaNueva.put("tipo", "Furgoneta");
        filaNueva.put("matricula", "1234ABC");
        filaNueva.put("categoria", "VEHICULO");

        Map<String, String> filaExistente = new HashMap<>();
        filaExistente.put("tipo", "Turismo");
        filaExistente.put("matricula", "9999XYZ");
        filaExistente.put("categoria", "VEHICULO");

        when(vehiculoImportService.procesarFilaValidada(any(FilaVehiculoImportada.class)))
                .thenReturn(false) // primera fila: no existia, se crea
                .thenReturn(true); // segunda fila: ya existia

        ResponseEntity<ImportVehiculosReportDTO> response =
                controller.confirmar(new ConfirmarImportPdfRequest(List.of(filaNueva, filaExistente)));

        ImportVehiculosReportDTO reporte = response.getBody();
        assertThat(reporte).isNotNull();
        assertThat(reporte.creados()).isEqualTo(1);
        assertThat(reporte.existentes()).isEqualTo(1);
        assertThat(reporte.errores()).isEmpty();
    }

    @Test
    @DisplayName("Confirmar reporta como error una fila invalida sin persistir nada")
    void confirmarReportaFilaInvalida() {
        Map<String, String> filaInvalida = new HashMap<>();
        filaInvalida.put("tipo", "Turismo");
        filaInvalida.put("categoria", "VEHICULO"); // sin matricula -> invalida

        ResponseEntity<ImportVehiculosReportDTO> response =
                controller.confirmar(new ConfirmarImportPdfRequest(List.of(filaInvalida)));

        ImportVehiculosReportDTO reporte = response.getBody();
        assertThat(reporte).isNotNull();
        assertThat(reporte.creados()).isZero();
        assertThat(reporte.existentes()).isZero();
        assertThat(reporte.errores()).hasSize(1);
    }
}
