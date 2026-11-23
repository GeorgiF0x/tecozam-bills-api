package com.tecozam.bills.vehiculo.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecozam.bills.shared.dto.ColumnaDTO;
import com.tecozam.bills.shared.dto.ConfirmarImportPdfRequest;
import com.tecozam.bills.shared.dto.FilaPreviewDTO;
import com.tecozam.bills.shared.dto.ImportPdfPreviewDTO;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.llm.LlmListaExtractorService;
import com.tecozam.bills.vehiculo.application.VehiculoImportService;
import com.tecozam.bills.vehiculo.dto.ImportVehiculosReportDTO;
import com.tecozam.bills.vehiculo.infrastructure.import_.FilaVehiculoImportada;
import com.tecozam.bills.vehiculo.infrastructure.import_.VehiculoXlsxRowParser;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Import de vehiculos/maquinaria por PDF+IA, con pantalla de revision previa
 * a confirmar el alta (ver odd/tasks/maestros-import-pdf-ia.md T3). A
 * diferencia de {@link VehiculoImportController} (Excel, directo a BD), aqui
 * la IA ({@link LlmListaExtractorService}) solo propone filas candidatas: la
 * validacion/dedupe/alta reusa exactamente lo mismo que el Excel
 * ({@link VehiculoXlsxRowParser#validar}, {@link VehiculoImportService#procesarFilaValidada})
 * para que ambos caminos nunca difieran en reglas de negocio.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/import/vehiculos/pdf")
@RequiredArgsConstructor
@Tag(name = "Admin Import")
public class VehiculoPdfImportController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<ColumnaDTO> COLUMNAS = List.of(
            new ColumnaDTO("tipo", "Tipo"),
            new ColumnaDTO("matricula", "Matrícula"),
            new ColumnaDTO("codigoObra", "Código obra"),
            new ColumnaDTO("categoria", "Categoría"),
            new ColumnaDTO("descripcion", "Descripción")
    );

    private static final String SYSTEM_PROMPT =
            "Eres un extractor de datos de listados de vehículos o maquinaria industrial desde un PDF. "
            + "Para cada vehículo/máquina que aparezca en el texto, extrae ÚNICAMENTE lo que aparezca "
            + "literalmente: tipo (ej. furgoneta, excavadora, grúa — si no aparece, null), matrícula (si tiene, "
            + "si no null — la maquinaria subcontratada sin matrícula no la lleva), codigoObra (código de obra al "
            + "que está asignada, si aparece), categoria (si el documento distingue explícitamente VEHICULO o "
            + "INDUSTRIAL_MAQUINARIA, usa ese valor en mayúsculas; si no se menciona, devuelve null), descripcion "
            + "(si aparece). No inventes ni derives datos que no estén en el texto.";

    private final LlmListaExtractorService extractorService;
    private final VehiculoImportService vehiculoImportService;
    private final VehiculoXlsxRowParser parser;

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Previsualiza el import de vehiculos/maquinaria desde un PDF (extraccion con IA)",
            description = "Extrae filas candidatas con IA y las valida con las mismas reglas del import Excel, "
                    + "SIN persistir nada todavia. El resultado se revisa/edita en el frontend antes de confirmar.")
    public ResponseEntity<ImportPdfPreviewDTO> preview(@RequestPart("file") MultipartFile file) throws Exception {
        byte[] bytes = leerArchivo(file);
        validarEsPdf(bytes);
        List<JsonNode> filasCrudas = extractorService.extraerFilas(bytes, SYSTEM_PROMPT, buildJsonSchema());
        return ResponseEntity.ok(new ImportPdfPreviewDTO(COLUMNAS, mapearYValidar(filasCrudas)));
    }

    @PostMapping("/confirmar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Confirma el alta de vehiculos/maquinaria revisados tras la previsualizacion por PDF",
            description = "Revalida cada fila con las mismas reglas del import Excel antes de persistir — nunca "
                    + "se confia en lo que el frontend marco como valido.")
    public ResponseEntity<ImportVehiculosReportDTO> confirmar(@RequestBody ConfirmarImportPdfRequest request) {
        long inicio = System.currentTimeMillis();
        int creados = 0;
        int existentes = 0;
        List<String> errores = new ArrayList<>();

        for (Map<String, String> campos : request.filas()) {
            Optional<FilaVehiculoImportada> parsed = parser.validar(
                    campos.get("tipo"),
                    campos.get("matricula"),
                    campos.get("codigoObra"),
                    campos.get("categoria"),
                    campos.get("descripcion"));

            if (parsed.isEmpty()) continue;
            FilaVehiculoImportada fila = parsed.get();

            if (!fila.esValida()) {
                errores.add(describirFila(fila) + " — " + fila.errorValidacion());
                continue;
            }

            if (vehiculoImportService.procesarFilaValidada(fila)) {
                existentes++;
            } else {
                creados++;
            }
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Import PDF vehiculos (confirmar): creados={}, existentes={}, errores={}, duracion={}ms",
                creados, existentes, errores.size(), duracion);
        return ResponseEntity.ok(new ImportVehiculosReportDTO(creados, existentes, errores, duracion));
    }

    /**
     * Mapea cada fila cruda devuelta por la IA a un {@link FilaPreviewDTO},
     * revalidando con {@link VehiculoXlsxRowParser#validar} (misma regla que
     * el Excel). Separado de {@link #preview} para poder testearlo con
     * {@link JsonNode} construidos a mano, sin llamar a la IA real.
     */
    List<FilaPreviewDTO> mapearYValidar(List<JsonNode> filasCrudas) {
        List<FilaPreviewDTO> resultado = new ArrayList<>();
        for (JsonNode fila : filasCrudas) {
            String tipo = texto(fila, "tipo");
            String matricula = texto(fila, "matricula");
            String codigoObra = texto(fila, "codigoObra");
            String categoria = texto(fila, "categoria");
            String descripcion = texto(fila, "descripcion");

            Optional<FilaVehiculoImportada> parsed = parser.validar(tipo, matricula, codigoObra, categoria, descripcion);
            if (parsed.isEmpty()) continue;
            FilaVehiculoImportada filaValidada = parsed.get();

            Map<String, String> campos = new HashMap<>();
            campos.put("tipo", vacioSiNull(filaValidada.tipo()));
            campos.put("matricula", vacioSiNull(filaValidada.matricula()));
            campos.put("codigoObra", vacioSiNull(filaValidada.codigoObra()));
            campos.put("categoria", vacioSiNull(filaValidada.categoria()));
            campos.put("descripcion", vacioSiNull(filaValidada.descripcion()));

            resultado.add(new FilaPreviewDTO(campos, filaValidada.esValida(), filaValidada.errorValidacion()));
        }
        return resultado;
    }

    private String describirFila(FilaVehiculoImportada fila) {
        if (fila.matricula() != null && !fila.matricula().isBlank()) return "Matricula " + fila.matricula();
        if (fila.codigoObra() != null && !fila.codigoObra().isBlank()) return "Codigo obra " + fila.codigoObra();
        return "Fila sin matricula ni codigo de obra";
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
        if (file.getSize() > 10L * 1024 * 1024) {
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
     * Schema de UNA fila (objeto, 5 campos nullable, additionalProperties
     * false, todos required) — mismo patron que
     * {@code LlmFacturaParser.buildJsonSchema()}. El array {@code filas} que
     * lo envuelve lo construye {@link LlmListaExtractorService}.
     */
    private ObjectNode buildJsonSchema() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);

        ObjectNode props = schema.putObject("properties");
        LlmListaExtractorService.nullableString(props, "tipo");
        LlmListaExtractorService.nullableString(props, "matricula");
        LlmListaExtractorService.nullableString(props, "codigoObra");
        LlmListaExtractorService.nullableString(props, "categoria");
        LlmListaExtractorService.nullableString(props, "descripcion");

        schema.putArray("required").add("tipo").add("matricula").add("codigoObra").add("categoria").add("descripcion");
        return schema;
    }
}
