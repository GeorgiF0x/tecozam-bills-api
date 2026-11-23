package com.tecozam.bills.tarjeta.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecozam.bills.shared.dto.ConfirmarImportPdfRequest;
import com.tecozam.bills.shared.dto.FilaPreviewDTO;
import com.tecozam.bills.shared.infrastructure.llm.LlmListaExtractorService;
import com.tecozam.bills.tarjeta.application.TarjetaImportService;
import com.tecozam.bills.tarjeta.application.TarjetaImportService.ResultadoFila;
import com.tecozam.bills.tarjeta.dto.ImportTarjetasAltaReportDTO;
import com.tecozam.bills.tarjeta.infrastructure.import_.TarjetaXlsxRowParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit test del controller de import PDF+IA de alta de tarjetas. No llama a
 * la IA real: {@link LlmListaExtractorService} se mockea pero
 * {@link #mapearYValidar} se testea directamente con {@link JsonNode}
 * construidos a mano (sin pasar por {@code extraerFilas}).
 */
@ExtendWith(MockitoExtension.class)
class TarjetaPdfImportControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private LlmListaExtractorService extractorService;

    @Mock
    private TarjetaImportService tarjetaImportService;

    private TarjetaXlsxRowParser parser;
    private TarjetaPdfImportController controller;

    @BeforeEach
    void setUp() {
        parser = new TarjetaXlsxRowParser();
        controller = new TarjetaPdfImportController(extractorService, parser, tarjetaImportService);
    }

    private JsonNode fila(String numeroTarjeta, String alias, String codigoProveedor) {
        var node = MAPPER.createObjectNode();
        node.put("numeroTarjeta", numeroTarjeta);
        node.put("alias", alias);
        node.put("codigoProveedor", codigoProveedor);
        return node;
    }

    // ── mapearYValidar (preview, sin IA real) ──────────────────────────

    @Test
    @DisplayName("Fila completa valida se mapea con valida=true")
    void filaCompletaValida() {
        List<FilaPreviewDTO> filas = controller.mapearYValidar(
                List.of(fila("1234567890", "Furgoneta 1", "REPSOL")));

        assertThat(filas).hasSize(1);
        FilaPreviewDTO f = filas.get(0);
        assertThat(f.valida()).isTrue();
        assertThat(f.error()).isNull();
        assertThat(f.campos()).containsEntry("numeroTarjeta", "1234567890");
        assertThat(f.campos()).containsEntry("alias", "Furgoneta 1");
        assertThat(f.campos()).containsEntry("codigoProveedor", "REPSOL");
    }

    @Test
    @DisplayName("Fila sin numero de tarjeta es invalida")
    void filaSinNumeroTarjetaEsInvalida() {
        List<FilaPreviewDTO> filas = controller.mapearYValidar(
                List.of(fila(null, "Furgoneta 1", "REPSOL")));

        assertThat(filas).hasSize(1);
        FilaPreviewDTO f = filas.get(0);
        assertThat(f.valida()).isFalse();
        assertThat(f.error()).contains("Numero Tarjeta");
    }

    @Test
    @DisplayName("Fila sin proveedor es invalida")
    void filaSinProveedorEsInvalida() {
        List<FilaPreviewDTO> filas = controller.mapearYValidar(
                List.of(fila("1234567890", "Furgoneta 1", null)));

        assertThat(filas).hasSize(1);
        FilaPreviewDTO f = filas.get(0);
        assertThat(f.valida()).isFalse();
        assertThat(f.error()).contains("Proveedor");
    }

    @Test
    @DisplayName("Fila completamente vacia se omite")
    void filaVaciaSeOmite() {
        List<FilaPreviewDTO> filas = controller.mapearYValidar(
                List.of(fila(null, null, null)));

        assertThat(filas).isEmpty();
    }

    // ── confirmar ────────────────────────────────────────────────────

    @Test
    @DisplayName("Confirmar cuenta creados, existentes y errores")
    void confirmarCuentaCreadosExistentesYErrores() {
        when(tarjetaImportService.procesarFilaValidada(argThatNumero("1111111111")))
                .thenReturn(ResultadoFila.creada());
        when(tarjetaImportService.procesarFilaValidada(argThatNumero("2222222222")))
                .thenReturn(ResultadoFila.existente());
        when(tarjetaImportService.procesarFilaValidada(argThatNumero("3333333333")))
                .thenReturn(ResultadoFila.error("Proveedor 'NOEXISTE' no registrado o inactivo"));

        ConfirmarImportPdfRequest request = new ConfirmarImportPdfRequest(List.of(
                Map.of("numeroTarjeta", "1111111111", "alias", "", "codigoProveedor", "REPSOL"),
                Map.of("numeroTarjeta", "2222222222", "alias", "", "codigoProveedor", "CEPSA"),
                Map.of("numeroTarjeta", "3333333333", "alias", "", "codigoProveedor", "NOEXISTE")
        ));

        ResponseEntity<ImportTarjetasAltaReportDTO> response = controller.confirmar(request);

        ImportTarjetasAltaReportDTO report = response.getBody();
        assertThat(report).isNotNull();
        assertThat(report.creados()).isEqualTo(1);
        assertThat(report.existentes()).isEqualTo(1);
        assertThat(report.errores()).hasSize(1);
        assertThat(report.errores().get(0)).contains("3333333333").contains("no registrado o inactivo");
    }

    @Test
    @DisplayName("Confirmar omite filas vacias y reporta invalidas sin llamar al service")
    void confirmarOmiteFilasVaciasYReportaInvalidas() {
        ConfirmarImportPdfRequest request = new ConfirmarImportPdfRequest(List.of(
                Map.of("numeroTarjeta", "", "alias", "", "codigoProveedor", ""),
                Map.of("numeroTarjeta", "", "alias", "Furgoneta", "codigoProveedor", "REPSOL")
        ));

        ResponseEntity<ImportTarjetasAltaReportDTO> response = controller.confirmar(request);

        ImportTarjetasAltaReportDTO report = response.getBody();
        assertThat(report).isNotNull();
        assertThat(report.creados()).isZero();
        assertThat(report.existentes()).isZero();
        assertThat(report.errores()).hasSize(1);
        assertThat(report.errores().get(0)).contains("Numero Tarjeta");
    }

    /** Matcher simple: la FilaTarjetaImportada pasada tiene este numeroTarjeta. */
    private com.tecozam.bills.tarjeta.infrastructure.import_.FilaTarjetaImportada argThatNumero(String numero) {
        return org.mockito.ArgumentMatchers.argThat(fila -> fila != null && numero.equals(fila.numeroTarjeta()));
    }
}
