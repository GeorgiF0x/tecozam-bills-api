package com.tecozam.bills.auth.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecozam.bills.auth.application.UsuarioImportService;
import com.tecozam.bills.auth.dto.ImportUsuariosReportDTO;
import com.tecozam.bills.auth.infrastructure.import_.FilaUsuarioImportada;
import com.tecozam.bills.auth.infrastructure.import_.UsuarioXlsxRowParser;
import com.tecozam.bills.shared.dto.ColumnaDTO;
import com.tecozam.bills.shared.dto.ConfirmarImportPdfRequest;
import com.tecozam.bills.shared.dto.FilaPreviewDTO;
import com.tecozam.bills.shared.dto.ImportPdfPreviewDTO;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.llm.LlmListaExtractorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Import de usuarios (oficina y campo) por PDF+IA, con pantalla de revision
 * previa a confirmar el alta (ver odd/tasks/maestros-import-pdf-ia.md T6). A
 * diferencia de {@link UsuarioImportController} (Excel, directo a BD), aqui
 * la IA ({@link LlmListaExtractorService}) solo propone filas candidatas: la
 * validacion/dedupe/alta reusa exactamente lo mismo que el Excel
 * ({@link UsuarioXlsxRowParser#validar}, {@link UsuarioImportService#procesarFilaValidada})
 * para que ambos caminos nunca difieran en reglas de negocio.
 *
 * <p><b>La contraseña NUNCA se manda a la IA</b> (decision de seguridad del
 * cliente): ni el prompt ni el schema de la fila la piden. En el preview, el
 * campo {@code password} de {@link FilaPreviewDTO#campos()} siempre llega
 * como cadena vacia — el gestor la escribe a mano en la tabla de revision del
 * frontend antes de confirmar. Es esperado (y correcto) que casi todas las
 * filas salgan invalidas en preview por "La contraseña es obligatoria": eso
 * fuerza a rellenarla antes de poder confirmar el alta.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/import/usuarios/pdf")
@RequiredArgsConstructor
@Tag(name = "Admin Import")
public class UsuarioPdfImportController {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<ColumnaDTO> COLUMNAS = List.of(
            new ColumnaDTO("tipo", "Tipo"),
            new ColumnaDTO("username", "Usuario"),
            new ColumnaDTO("nombre", "Nombre"),
            new ColumnaDTO("apellidos", "Apellidos"),
            new ColumnaDTO("email", "Email"),
            new ColumnaDTO("dni", "DNI"),
            new ColumnaDTO("telefono", "Teléfono"),
            new ColumnaDTO("rol", "Rol"),
            new ColumnaDTO("password", "Contraseña")
    );

    private static final String SYSTEM_PROMPT =
            "Eres un extractor de datos de listados de usuarios del sistema desde un PDF (usuarios de oficina o "
                    + "de campo). Para cada usuario que aparezca en el texto, extrae ÚNICAMENTE lo que aparezca "
                    + "literalmente: tipo (OFICINA o CAMPO si se distingue explícitamente, si no null), username, "
                    + "nombre, apellidos (solo aplica a campo), email (solo aplica a oficina, si aparece), dni (si "
                    + "aparece), telefono (solo aplica a campo, si aparece), rol (solo aplica a oficina: ADMIN, "
                    + "GESTOR o CONSULTA, si aparece explícitamente). NO extraigas ninguna contraseña aunque "
                    + "aparezca en el texto — ese campo se rellena manualmente después por seguridad, ignóralo por "
                    + "completo. No inventes ni derives datos que no estén en el texto.";

    private final LlmListaExtractorService extractorService;
    private final UsuarioImportService usuarioImportService;
    private final UsuarioXlsxRowParser parser;

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Previsualiza el import de usuarios desde un PDF (extraccion con IA)",
            description = "Extrae filas candidatas con IA (SIN pedir nunca la contraseña) y las valida con las "
                    + "mismas reglas del import Excel, SIN persistir nada todavia. El campo password siempre "
                    + "llega vacio; el gestor debe rellenarlo a mano antes de confirmar.")
    public ResponseEntity<ImportPdfPreviewDTO> preview(@RequestPart("file") MultipartFile file) throws Exception {
        byte[] bytes = leerArchivo(file);
        validarEsPdf(bytes);
        List<JsonNode> filasCrudas = extractorService.extraerFilas(bytes, SYSTEM_PROMPT, buildFilaSchema());
        return ResponseEntity.ok(new ImportPdfPreviewDTO(COLUMNAS, mapearYValidar(filasCrudas)));
    }

    @PostMapping("/confirmar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Confirma el alta de usuarios revisados tras la previsualizacion por PDF",
            description = "Revalida cada fila con las mismas reglas del import Excel antes de persistir — nunca "
                    + "se confia en lo que el frontend marco como valido. Aqui si se espera la contraseña real, "
                    + "rellenada a mano por el gestor en la pantalla de revision.")
    public ResponseEntity<ImportUsuariosReportDTO> confirmar(@RequestBody ConfirmarImportPdfRequest request) {
        return ResponseEntity.ok(confirmarFilas(request));
    }

    /**
     * Mapea cada fila cruda devuelta por la IA a un {@link FilaPreviewDTO},
     * revalidando con {@link UsuarioXlsxRowParser#validar} — SIEMPRE con
     * password vacio, porque la IA nunca lo extrae. Separado de
     * {@link #preview} para poder testearlo con {@link JsonNode}
     * construidos a mano, sin llamar a la IA real.
     */
    List<FilaPreviewDTO> mapearYValidar(List<JsonNode> filasCrudas) {
        List<FilaPreviewDTO> resultado = new ArrayList<>();
        for (JsonNode fila : filasCrudas) {
            String tipo = texto(fila, "tipo");
            String username = texto(fila, "username");
            String nombre = texto(fila, "nombre");
            String apellidos = texto(fila, "apellidos");
            String email = texto(fila, "email");
            String dni = texto(fila, "dni");
            String telefono = texto(fila, "telefono");
            String rol = texto(fila, "rol");

            Optional<FilaUsuarioImportada> parsed = parser.validar(
                    tipo, username, "", nombre, apellidos, email, dni, telefono, rol);
            if (parsed.isEmpty()) continue;
            FilaUsuarioImportada filaValidada = parsed.get();

            Map<String, String> campos = new LinkedHashMap<>();
            campos.put("tipo", vacioSiNull(filaValidada.tipo()));
            campos.put("username", vacioSiNull(filaValidada.username()));
            campos.put("nombre", vacioSiNull(filaValidada.nombre()));
            campos.put("apellidos", vacioSiNull(filaValidada.apellidos()));
            campos.put("email", vacioSiNull(filaValidada.email()));
            campos.put("dni", vacioSiNull(filaValidada.dni()));
            campos.put("telefono", vacioSiNull(filaValidada.telefono()));
            campos.put("rol", vacioSiNull(filaValidada.rol()));
            campos.put("password", "");

            resultado.add(new FilaPreviewDTO(campos, filaValidada.esValida(), filaValidada.errorValidacion()));
        }
        return resultado;
    }

    /**
     * Revalida y persiste cada fila ya revisada/editada por el usuario —
     * aqui si con la contraseña real que el gestor rellenó a mano en la
     * pantalla de revision. Separado de {@link #confirmar} por el mismo
     * motivo que {@link #mapearYValidar}: testeable sin contexto Spring.
     */
    ImportUsuariosReportDTO confirmarFilas(ConfirmarImportPdfRequest request) {
        long inicio = System.currentTimeMillis();

        int creados = 0;
        int existentes = 0;
        List<String> errores = new ArrayList<>();

        List<Map<String, String>> filas = request.filas() == null ? List.of() : request.filas();
        for (Map<String, String> campos : filas) {
            Optional<FilaUsuarioImportada> parsed = parser.validar(
                    campos.get("tipo"),
                    campos.get("username"),
                    campos.get("password"),
                    campos.get("nombre"),
                    campos.get("apellidos"),
                    campos.get("email"),
                    campos.get("dni"),
                    campos.get("telefono"),
                    campos.get("rol"));
            if (parsed.isEmpty()) continue;
            FilaUsuarioImportada fila = parsed.get();

            if (!fila.esValida()) {
                errores.add(describirFila(fila) + " — " + fila.errorValidacion());
                continue;
            }

            if (usuarioImportService.procesarFilaValidada(fila)) {
                existentes++;
            } else {
                creados++;
            }
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Import PDF+IA usuarios (confirmar): creados={}, existentes={}, errores={}, duracion={}ms",
                creados, existentes, errores.size(), duracion);
        return new ImportUsuariosReportDTO(creados, existentes, errores, duracion);
    }

    private String describirFila(FilaUsuarioImportada fila) {
        return (fila.username() != null && !fila.username().isBlank())
                ? "Usuario " + fila.username()
                : "Fila sin username";
    }

    private String vacioSiNull(String s) {
        return s == null ? "" : s;
    }

    private String texto(JsonNode fila, String campo) {
        JsonNode v = fila.get(campo);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    private byte[] leerArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("El archivo es obligatorio");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException("El archivo no puede superar 10 MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el archivo", e);
        }
    }

    private void validarEsPdf(byte[] bytes) {
        if (!esPdf(bytes)) {
            throw new BusinessException("El archivo debe ser un PDF válido");
        }
    }

    private boolean esPdf(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    /**
     * Schema de UNA fila de usuario para el modo {@code json_schema}
     * estricto de OpenAI (mismo patron que {@code VehiculoPdfImportController
     * .buildJsonSchema()}) — NO incluye {@code password}: por seguridad esa
     * contraseña nunca se manda a la IA, el gestor la rellena a mano.
     */
    private ObjectNode buildFilaSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode props = schema.putObject("properties");
        LlmListaExtractorService.nullableString(props, "tipo");
        LlmListaExtractorService.nullableString(props, "username");
        LlmListaExtractorService.nullableString(props, "nombre");
        LlmListaExtractorService.nullableString(props, "apellidos");
        LlmListaExtractorService.nullableString(props, "email");
        LlmListaExtractorService.nullableString(props, "dni");
        LlmListaExtractorService.nullableString(props, "telefono");
        LlmListaExtractorService.nullableString(props, "rol");

        schema.putArray("required")
                .add("tipo").add("username").add("nombre").add("apellidos")
                .add("email").add("dni").add("telefono").add("rol");
        return schema;
    }
}
