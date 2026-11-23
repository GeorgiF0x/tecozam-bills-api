package com.tecozam.bills.viat.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecozam.bills.shared.dto.ColumnaDTO;
import com.tecozam.bills.shared.dto.ConfirmarImportPdfRequest;
import com.tecozam.bills.shared.dto.FilaPreviewDTO;
import com.tecozam.bills.shared.dto.ImportPdfPreviewDTO;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.llm.LlmListaExtractorService;
import com.tecozam.bills.viat.application.ViatImportService;
import com.tecozam.bills.viat.dto.ImportViatsReportDTO;
import com.tecozam.bills.viat.infrastructure.import_.FilaViatImportada;
import com.tecozam.bills.viat.infrastructure.import_.ViatXlsxRowParser;
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
 * Import de VIATs por PDF + IA (ver odd/tasks/maestros-import-pdf-ia.md T5).
 * A diferencia del import Excel ({@link ViatImportController}, que persiste
 * directo), este flujo tiene dos pasos: {@code /preview} extrae las filas
 * candidatas con {@link LlmListaExtractorService} y las valida SIN tocar la
 * base de datos, para que el gestor las revise/corrija en una tabla
 * editable; {@code /confirmar} revalida cada fila (el frontend pudo
 * editarlas) y recien ahi persiste con
 * {@link ViatImportService#procesarFilaValidada}.
 *
 * <p>La validacion de negocio es SIEMPRE la misma que usa el import Excel
 * ({@link ViatXlsxRowParser#validar}) — este controller solo cambia de donde
 * vienen los datos de la fila (PDF+IA en vez de celdas de Excel).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/import/viats/pdf")
@RequiredArgsConstructor
@Tag(name = "Admin Import")
public class ViatPdfImportController {

    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<ColumnaDTO> COLUMNAS = List.of(
            new ColumnaDTO("codigo", "Código"),
            new ColumnaDTO("numeroSerie", "Número de serie"),
            new ColumnaDTO("descripcion", "Descripción")
    );

    private static final String SYSTEM_PROMPT =
            "Eres un extractor de datos de listados de dispositivos VIAT (telepeaje) desde un PDF. Para cada "
                    + "dispositivo que aparezca en el texto, extrae ÚNICAMENTE lo que aparezca literalmente: "
                    + "codigo (código identificador del dispositivo), numeroSerie (si aparece, si no null), "
                    + "descripcion (si aparece, si no null). No inventes ni derives datos que no estén en el texto.";

    private final ViatXlsxRowParser parser;
    private final LlmListaExtractorService llmListaExtractorService;
    private final ViatImportService viatImportService;

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Extrae con IA las filas candidatas de VIATs de un PDF, sin persistir",
            description = "Devuelve las filas extraídas ya validadas con las mismas reglas del import Excel, "
                    + "para que el gestor las revise/corrija antes de confirmar el alta.")
    public ResponseEntity<ImportPdfPreviewDTO> preview(@RequestPart("file") MultipartFile file) {
        byte[] bytes = validarEsPdf(file);

        List<JsonNode> filasCrudas;
        try {
            filasCrudas = llmListaExtractorService.extraerFilas(bytes, SYSTEM_PROMPT, buildJsonSchema());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ViatPdfImportController] Error extrayendo filas del PDF: {}", e.getMessage(), e);
            throw new BusinessException(
                    "No se pudo procesar el PDF de VIATs. Verifica que el archivo sea correcto y "
                            + "vuelve a intentarlo. Detalle técnico: " + e.getMessage());
        }

        return ResponseEntity.ok(new ImportPdfPreviewDTO(COLUMNAS, mapearYValidar(filasCrudas)));
    }

    @PostMapping("/confirmar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Confirma y persiste las filas de VIATs revisadas tras el import PDF+IA",
            description = "Revalida cada fila con las mismas reglas del import Excel — nunca confía en lo que "
                    + "el frontend marcó como válida, porque el gestor pudo haberla editado.")
    public ResponseEntity<ImportViatsReportDTO> confirmar(@RequestBody ConfirmarImportPdfRequest request) {
        long inicio = System.currentTimeMillis();

        int creados = 0;
        int existentes = 0;
        List<String> errores = new ArrayList<>();

        List<Map<String, String>> filas = request.filas() == null ? List.of() : request.filas();
        for (Map<String, String> campos : filas) {
            Optional<FilaViatImportada> validada = parser.validar(
                    campos.get("codigo"), campos.get("numeroSerie"), campos.get("descripcion"));
            if (validada.isEmpty()) continue;

            FilaViatImportada fila = validada.get();
            if (!fila.esValida()) {
                errores.add(describirFila(fila) + " — " + fila.errorValidacion());
                continue;
            }

            if (viatImportService.procesarFilaValidada(fila)) {
                existentes++;
            } else {
                creados++;
            }
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Import PDF+IA viats: creados={}, existentes={}, errores={}, duracion={}ms",
                creados, existentes, errores.size(), duracion);
        return ResponseEntity.ok(new ImportViatsReportDTO(creados, existentes, errores, duracion));
    }

    /**
     * Mapea cada fila cruda devuelta por la IA a {@link FilaPreviewDTO},
     * revalidando con {@link ViatXlsxRowParser#validar}. Separado de
     * {@link #preview} para poder testearlo con {@link JsonNode} construidos
     * a mano, sin mockear la llamada HTTP a OpenAI.
     */
    List<FilaPreviewDTO> mapearYValidar(List<JsonNode> filasCrudas) {
        List<FilaPreviewDTO> filas = new ArrayList<>();
        for (JsonNode nodo : filasCrudas) {
            String codigo = texto(nodo, "codigo");
            String numeroSerie = texto(nodo, "numeroSerie");
            String descripcion = texto(nodo, "descripcion");

            Optional<FilaViatImportada> validada = parser.validar(codigo, numeroSerie, descripcion);
            if (validada.isEmpty()) continue;

            FilaViatImportada fila = validada.get();
            Map<String, String> campos = new LinkedHashMap<>();
            campos.put("codigo", vacioSiNull(fila.codigo()));
            campos.put("numeroSerie", vacioSiNull(fila.numeroSerie()));
            campos.put("descripcion", vacioSiNull(fila.descripcion()));

            filas.add(new FilaPreviewDTO(campos, fila.esValida(), fila.errorValidacion()));
        }
        return filas;
    }

    private String describirFila(FilaViatImportada fila) {
        if (fila.codigo() != null && !fila.codigo().isBlank()) return "Codigo " + fila.codigo();
        return "Fila sin codigo";
    }

    private String texto(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        if (valor == null || valor.isNull()) return null;
        return valor.asText();
    }

    private String vacioSiNull(String valor) {
        return valor == null ? "" : valor;
    }

    private byte[] validarEsPdf(MultipartFile file) {
        byte[] bytes = leerArchivo(file);
        if (!esPdf(bytes)) {
            throw new BusinessException("El archivo debe ser un PDF");
        }
        return bytes;
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
            throw new BusinessException("Archivo inválido o ilegible", e);
        }
    }

    private boolean esPdf(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    /**
     * Schema de UNA fila de VIAT para el modo {@code json_schema} estricto de
     * OpenAI (mismo patrón que {@code LlmFacturaParser.buildJsonSchema()}):
     * todas las propiedades son {@code required}, y al ser todas opcionales
     * en el dominio (solo {@code codigo} es obligatorio por regla de
     * negocio, no por el schema) se declaran las tres como nullable.
     */
    private ObjectNode buildJsonSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode props = schema.putObject("properties");
        LlmListaExtractorService.nullableString(props, "codigo");
        LlmListaExtractorService.nullableString(props, "numeroSerie");
        LlmListaExtractorService.nullableString(props, "descripcion");

        schema.putArray("required").add("codigo").add("numeroSerie").add("descripcion");
        return schema;
    }
}
