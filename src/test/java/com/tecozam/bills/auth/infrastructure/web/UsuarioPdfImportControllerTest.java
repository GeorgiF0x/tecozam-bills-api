package com.tecozam.bills.auth.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecozam.bills.auth.application.UsuarioImportService;
import com.tecozam.bills.auth.dto.ImportUsuariosReportDTO;
import com.tecozam.bills.auth.infrastructure.import_.UsuarioXlsxRowParser;
import com.tecozam.bills.shared.dto.ConfirmarImportPdfRequest;
import com.tecozam.bills.shared.dto.FilaPreviewDTO;
import com.tecozam.bills.shared.infrastructure.llm.LlmListaExtractorService;
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
 * Unit test del import PDF+IA de usuarios (oficina/campo) — ver
 * odd/tasks/maestros-import-pdf-ia.md T6. Solo prueba la logica de mapeo +
 * validacion ({@code mapearYValidar}, package-private) y de confirmacion
 * ({@code confirmarFilas}, package-private) con un {@link UsuarioXlsxRowParser}
 * real (funcion pura, sin BD) — nunca llama a la IA real.
 *
 * <p>La contraseña NUNCA se manda a la IA: en preview ({@code mapearYValidar})
 * siempre se valida con password vacio a proposito, por lo que casi toda fila
 * de preview sale invalida por "La contraseña es obligatoria" — eso es lo
 * esperado. Los tests de reglas de negocio puras sobre el campo rol (OFICINA
 * vs CAMPO) se hacen via {@code confirmarFilas}, donde si llega una
 * contraseña real rellenada a mano por el gestor.
 */
@ExtendWith(MockitoExtension.class)
class UsuarioPdfImportControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PASSWORD_VALIDO = "Abcd1234";

    @Mock
    private LlmListaExtractorService llmListaExtractorService;
    @Mock
    private UsuarioImportService usuarioImportService;

    private UsuarioPdfImportController controller;

    @BeforeEach
    void setUp() {
        controller = new UsuarioPdfImportController(
                llmListaExtractorService, usuarioImportService, new UsuarioXlsxRowParser());
    }

    private ObjectNode filaCruda(String tipo, String username, String nombre, String apellidos, String email,
                                  String dni, String telefono, String rol) {
        ObjectNode nodo = MAPPER.createObjectNode();
        ponerOTexto(nodo, "tipo", tipo);
        ponerOTexto(nodo, "username", username);
        ponerOTexto(nodo, "nombre", nombre);
        ponerOTexto(nodo, "apellidos", apellidos);
        ponerOTexto(nodo, "email", email);
        ponerOTexto(nodo, "dni", dni);
        ponerOTexto(nodo, "telefono", telefono);
        ponerOTexto(nodo, "rol", rol);
        return nodo;
    }

    private void ponerOTexto(ObjectNode nodo, String campo, String valor) {
        if (valor == null) {
            nodo.putNull(campo);
        } else {
            nodo.put(campo, valor);
        }
    }

    private Map<String, String> filaConfirmada(String tipo, String username, String password, String nombre,
                                                 String apellidos, String email, String dni, String telefono,
                                                 String rol) {
        Map<String, String> campos = new LinkedHashMap<>();
        if (tipo != null) campos.put("tipo", tipo);
        if (username != null) campos.put("username", username);
        if (password != null) campos.put("password", password);
        if (nombre != null) campos.put("nombre", nombre);
        if (apellidos != null) campos.put("apellidos", apellidos);
        if (email != null) campos.put("email", email);
        if (dni != null) campos.put("dni", dni);
        if (telefono != null) campos.put("telefono", telefono);
        if (rol != null) campos.put("rol", rol);
        return campos;
    }

    @Test
    @DisplayName("mapearYValidar: fila OFICINA completa pero sin password sale invalida (caso normal de preview)")
    void previewFilaOficinaSinPasswordEsInvalida() {
        List<JsonNode> crudas = List.of(
                filaCruda("OFICINA", "jgarcia", "Juan", null, "juan@test.com", "12345678A", null, "ADMIN"));

        List<FilaPreviewDTO> filas = controller.mapearYValidar(crudas);

        assertThat(filas).hasSize(1);
        FilaPreviewDTO fila = filas.get(0);
        assertThat(fila.valida()).isFalse();
        assertThat(fila.error()).isEqualTo("La contraseña es obligatoria");
        assertThat(fila.campos())
                .containsEntry("tipo", "OFICINA")
                .containsEntry("username", "jgarcia")
                .containsEntry("nombre", "Juan")
                .containsEntry("password", "");
    }

    @Test
    @DisplayName("confirmarFilas: la misma fila OFICINA con password valido relleno a mano sale valida")
    void confirmarFilaOficinaConPasswordValidoEsValida() {
        when(usuarioImportService.procesarFilaValidada(any())).thenReturn(false);

        ConfirmarImportPdfRequest request = new ConfirmarImportPdfRequest(List.of(
                filaConfirmada("OFICINA", "jgarcia", PASSWORD_VALIDO, "Juan", null, "juan@test.com",
                        "12345678A", null, "ADMIN")));

        ImportUsuariosReportDTO reporte = controller.confirmarFilas(request);

        assertThat(reporte.errores()).isEmpty();
        assertThat(reporte.creados()).isEqualTo(1);
        assertThat(reporte.existentes()).isZero();
    }

    @Test
    @DisplayName("confirmarFilas: fila CAMPO sin rol es valida — rol no aplica a campo")
    void confirmarFilaCampoSinRolEsValida() {
        when(usuarioImportService.procesarFilaValidada(any())).thenReturn(false);

        ConfirmarImportPdfRequest request = new ConfirmarImportPdfRequest(List.of(
                filaConfirmada("CAMPO", "mlopez", PASSWORD_VALIDO, "Maria", "Lopez Diaz", null,
                        "87654321B", "600111222", null)));

        ImportUsuariosReportDTO reporte = controller.confirmarFilas(request);

        assertThat(reporte.errores()).isEmpty();
        assertThat(reporte.creados()).isEqualTo(1);
    }

    @Test
    @DisplayName("confirmarFilas: fila OFICINA sin rol es invalida — rol es obligatorio ahi")
    void confirmarFilaOficinaSinRolEsInvalida() {
        ConfirmarImportPdfRequest request = new ConfirmarImportPdfRequest(List.of(
                filaConfirmada("OFICINA", "jgarcia", PASSWORD_VALIDO, "Juan", null, "juan@test.com",
                        "12345678A", null, null)));

        ImportUsuariosReportDTO reporte = controller.confirmarFilas(request);

        assertThat(reporte.creados()).isZero();
        assertThat(reporte.existentes()).isZero();
        assertThat(reporte.errores()).hasSize(1);
        assertThat(reporte.errores().get(0))
                .contains("jgarcia")
                .contains("El rol es obligatorio para usuarios de oficina");
    }

    @Test
    @DisplayName("mapearYValidar: fila completamente vacia se omite del resultado")
    void previewFilaVaciaSeOmite() {
        List<JsonNode> crudas = List.of(
                filaCruda(null, null, null, null, null, null, null, null),
                filaCruda("CAMPO", "mlopez", "Maria", "Lopez", null, null, null, null));

        List<FilaPreviewDTO> filas = controller.mapearYValidar(crudas);

        assertThat(filas).hasSize(1);
        assertThat(filas.get(0).campos()).containsEntry("username", "mlopez");
    }

    @Test
    @DisplayName("confirmarFilas: cuenta creados/existentes correctamente con una fila nueva y una existente")
    void confirmarFilasCuentaBien() {
        when(usuarioImportService.procesarFilaValidada(any())).thenReturn(false, true);

        ConfirmarImportPdfRequest request = new ConfirmarImportPdfRequest(List.of(
                filaConfirmada("OFICINA", "jgarcia", PASSWORD_VALIDO, "Juan", null, "juan@test.com",
                        "12345678A", null, "ADMIN"),
                filaConfirmada("CAMPO", "mlopez", PASSWORD_VALIDO, "Maria", "Lopez Diaz", null,
                        "87654321B", "600111222", null)));

        ImportUsuariosReportDTO reporte = controller.confirmarFilas(request);

        assertThat(reporte.creados()).isEqualTo(1);
        assertThat(reporte.existentes()).isEqualTo(1);
        assertThat(reporte.errores()).isEmpty();
    }
}
