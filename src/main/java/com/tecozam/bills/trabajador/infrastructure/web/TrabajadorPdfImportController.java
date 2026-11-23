package com.tecozam.bills.trabajador.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecozam.bills.shared.dto.ColumnaDTO;
import com.tecozam.bills.shared.dto.ConfirmarImportPdfRequest;
import com.tecozam.bills.shared.dto.FilaPreviewDTO;
import com.tecozam.bills.shared.dto.ImportPdfPreviewDTO;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.llm.LlmListaExtractorService;
import com.tecozam.bills.trabajador.application.TrabajadorImportService;
import com.tecozam.bills.trabajador.dto.ImportTrabajadoresReportDTO;
import com.tecozam.bills.trabajador.infrastructure.import_.FilaTrabajadorImportada;
import com.tecozam.bills.trabajador.infrastructure.import_.TrabajadorXlsxRowParser;
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
 * Import de trabajadores por PDF + IA (ver odd/tasks/maestros-import-pdf-ia.md
 * T2). A diferencia del import Excel ({@link TrabajadorImportController}, que
 * persiste directo), este flujo tiene dos pasos: {@code /preview} extrae las
 * filas candidatas con {@link LlmListaExtractorService} y las valida SIN
 * tocar la base de datos, para que el gestor las revise/corrija en una tabla
 * editable; {@code /confirmar} revalida cada fila (el frontend pudo editarlas)
 * y recien ahi persiste con {@link TrabajadorImportService#procesarFilaValidada}.
 *
 * <p>La validacion de negocio es SIEMPRE la misma que usa el import Excel
 * ({@link TrabajadorXlsxRowParser#validar}) — este controller solo cambia de
 * donde vienen los datos de la fila (PDF+IA en vez de celdas de Excel).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/import/trabajadores/pdf")
@RequiredArgsConstructor
@Tag(name = "Admin Import")
public class TrabajadorPdfImportController {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<ColumnaDTO> COLUMNAS = List.of(
            new ColumnaDTO("nombre", "Nombre"),
            new ColumnaDTO("apellidos", "Apellidos"),
            new ColumnaDTO("email", "Email"),
            new ColumnaDTO("dniNie", "DNI/NIE"),
            new ColumnaDTO("origen", "Origen")
    );

    private static final String SYSTEM_PROMPT =
            "Eres un extractor de datos de listas de trabajadores desde un PDF (ej. listado de personal de "
                    + "una subcontrata). Para cada trabajador que aparezca en el texto, extrae ÚNICAMENTE lo que "
                    + "aparezca literalmente: nombre, apellidos, email (si aparece, si no null), dniNie (DNI o NIE "
                    + "si aparece, si no null), origen (si el documento distingue explícitamente OFICINA o CAMPO, "
                    + "usa ese valor en mayúsculas; si no se menciona, devuelve null). No inventes ni derives "
                    + "datos que no estén en el texto.";

    private final TrabajadorXlsxRowParser parser;
    private final LlmListaExtractorService llmListaExtractorService;
    private final TrabajadorImportService trabajadorImportService;

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Extrae con IA las filas candidatas de trabajadores de un PDF, sin persistir",
            description = "Devuelve las filas extraídas ya validadas con las mismas reglas del import Excel, "
                    + "para que el gestor las revise/corrija antes de confirmar el alta.")
    public ResponseEntity<ImportPdfPreviewDTO> previsualizar(@RequestPart("file") MultipartFile file) {
        byte[] bytes = validarYLeer(file);

        List<JsonNode> filasCrudas;
        try {
            filasCrudas = llmListaExtractorService.extraerFilas(bytes, SYSTEM_PROMPT, buildFilaSchema());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TrabajadorPdfImportController] Error extrayendo filas del PDF: {}", e.getMessage(), e);
            throw new BusinessException(
                    "No se pudo procesar el PDF de trabajadores. Verifica que el archivo sea correcto y "
                            + "vuelve a intentarlo. Detalle técnico: " + e.getMessage());
        }

        return ResponseEntity.ok(new ImportPdfPreviewDTO(COLUMNAS, mapearYValidar(filasCrudas)));
    }

    @PostMapping("/confirmar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Confirma y persiste las filas de trabajadores revisadas tras el import PDF+IA",
            description = "Revalida cada fila con las mismas reglas del import Excel — nunca confía en lo que "
                    + "el frontend marcó como válida, porque el gestor pudo haberla editado.")
    public ResponseEntity<ImportTrabajadoresReportDTO> confirmar(@RequestBody ConfirmarImportPdfRequest request) {
        return ResponseEntity.ok(confirmarFilas(request));
    }

    /**
     * Mapea cada fila cruda devuelta por la IA a {@link FilaPreviewDTO},
     * revalidando con {@link TrabajadorXlsxRowParser#validar}. Separado de
     * {@link #previsualizar} para poder testearlo con {@link JsonNode}
     * construidos a mano, sin mockear la llamada HTTP a OpenAI.
     */
    List<FilaPreviewDTO> mapearYValidar(List<JsonNode> filasCrudas) {
        List<FilaPreviewDTO> filas = new ArrayList<>();
        for (JsonNode nodo : filasCrudas) {
            String nombre = textoOCampoAusente(nodo, "nombre");
            String apellidos = textoOCampoAusente(nodo, "apellidos");
            String email = textoOCampoAusente(nodo, "email");
            String dniNie = textoOCampoAusente(nodo, "dniNie");
            String origen = textoOCampoAusente(nodo, "origen");

            Optional<FilaTrabajadorImportada> validada = parser.validar(nombre, apellidos, email, dniNie, origen);
            if (validada.isEmpty()) continue;

            FilaTrabajadorImportada fila = validada.get();
            Map<String, String> campos = new LinkedHashMap<>();
            campos.put("nombre", vacioSiNulo(fila.nombre()));
            campos.put("apellidos", vacioSiNulo(fila.apellidos()));
            campos.put("email", vacioSiNulo(fila.email()));
            campos.put("dniNie", vacioSiNulo(fila.dniNie()));
            campos.put("origen", fila.origen() == null ? "" : fila.origen().name());

            filas.add(new FilaPreviewDTO(campos, fila.esValida(), fila.errorValidacion()));
        }
        return filas;
    }

    /**
     * Revalida y persiste cada fila ya revisada/editada por el usuario.
     * Separado de {@link #confirmar} por el mismo motivo que
     * {@link #mapearYValidar}: testeable sin necesidad de contexto Spring.
     */
    ImportTrabajadoresReportDTO confirmarFilas(ConfirmarImportPdfRequest request) {
        long inicio = System.currentTimeMillis();

        int creados = 0;
        int existentes = 0;
        List<String> errores = new ArrayList<>();

        List<Map<String, String>> filas = request.filas() == null ? List.of() : request.filas();
        for (Map<String, String> campos : filas) {
            Optional<FilaTrabajadorImportada> validada = parser.validar(
                    campos.get("nombre"), campos.get("apellidos"), campos.get("email"),
                    campos.get("dniNie"), campos.get("origen"));
            if (validada.isEmpty()) continue;

            FilaTrabajadorImportada fila = validada.get();
            if (!fila.esValida()) {
                errores.add(describirFila(fila) + " — " + fila.errorValidacion());
                continue;
            }

            if (trabajadorImportService.procesarFilaValidada(fila)) {
                existentes++;
            } else {
                creados++;
            }
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Import PDF+IA trabajadores: creados={}, existentes={}, errores={}, duracion={}ms",
                creados, existentes, errores.size(), duracion);
        return new ImportTrabajadoresReportDTO(creados, existentes, errores, duracion);
    }

    private String describirFila(FilaTrabajadorImportada fila) {
        String nombre = fila.nombre() == null ? "" : fila.nombre();
        String apellidos = fila.apellidos() == null ? "" : fila.apellidos();
        String full = (nombre + " " + apellidos).trim();
        return full.isBlank() ? "Fila sin nombre ni apellidos" : full;
    }

    private String textoOCampoAusente(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        if (valor == null || valor.isNull()) return null;
        return valor.asText();
    }

    private String vacioSiNulo(String valor) {
        return valor == null ? "" : valor;
    }

    private byte[] validarYLeer(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("El archivo es obligatorio");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException("El archivo no puede superar 10 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("Archivo inválido o ilegible", e);
        }
        if (!esPdf(bytes)) {
            throw new BusinessException("El archivo debe ser un PDF");
        }
        return bytes;
    }

    private boolean esPdf(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    /**
     * Schema de UNA fila de trabajador para el modo {@code json_schema}
     * estricto de OpenAI (mismo patrón que
     * {@code LlmFacturaParser.buildJsonSchema()}): todas las propiedades son
     * {@code required}, las opcionales son nullable en vez de ausentes.
     */
    private ObjectNode buildFilaSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode props = schema.putObject("properties");
        props.putObject("nombre").put("type", "string");
        props.putObject("apellidos").put("type", "string");
        LlmListaExtractorService.nullableString(props, "email");
        LlmListaExtractorService.nullableString(props, "dniNie");
        LlmListaExtractorService.nullableString(props, "origen");

        schema.putArray("required").add("nombre").add("apellidos").add("email").add("dniNie").add("origen");
        return schema;
    }
}
