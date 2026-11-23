package com.tecozam.bills.tarjeta.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tecozam.bills.shared.dto.ColumnaDTO;
import com.tecozam.bills.shared.dto.ConfirmarImportPdfRequest;
import com.tecozam.bills.shared.dto.FilaPreviewDTO;
import com.tecozam.bills.shared.dto.ImportPdfPreviewDTO;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.llm.LlmListaExtractorService;
import com.tecozam.bills.tarjeta.application.TarjetaImportService;
import com.tecozam.bills.tarjeta.application.TarjetaImportService.ResultadoFila;
import com.tecozam.bills.tarjeta.dto.ImportTarjetasAltaReportDTO;
import com.tecozam.bills.tarjeta.infrastructure.import_.FilaTarjetaImportada;
import com.tecozam.bills.tarjeta.infrastructure.import_.TarjetaXlsxRowParser;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Import de alta de tarjetas MAESTRO (cualquier proveedor ya dado de alta en
 * el sistema) desde un PDF usando IA, con pantalla de revisión previa a
 * confirmar — ver {@code odd/tasks/maestros-import-pdf-ia.md} T4.
 *
 * <p>Distinto de {@code ListadoTarjetasImportController} (import del listado
 * de MOVIMIENTOS de un proveedor concreto, con alias SOLRED/CEPSA
 * específicos de combustible): esto es alta de tarjetas, otro dominio.
 *
 * <p>La extracción IA (vía {@link LlmListaExtractorService}) no valida
 * reglas de negocio; la validación es siempre la misma que usa el import
 * Excel ({@link TarjetaXlsxRowParser#validar}), para no duplicar reglas. En
 * la fase de previsualización el proveedor NO se resuelve contra BD (igual
 * que el import Excel) — una fila puede marcarse válida aquí y aun así
 * fallar al confirmar si el proveedor no existe o está inactivo.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/import/tarjetas/pdf")
@RequiredArgsConstructor
@Tag(name = "Admin Import")
public class TarjetaPdfImportController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final long TAMANO_MAXIMO_BYTES = 10L * 1024 * 1024;

    private static final List<ColumnaDTO> COLUMNAS = List.of(
            new ColumnaDTO("numeroTarjeta", "Número tarjeta"),
            new ColumnaDTO("alias", "Alias"),
            new ColumnaDTO("codigoProveedor", "Proveedor")
    );

    private static final String SYSTEM_PROMPT =
            "Eres un extractor de datos de listados de tarjetas de combustible/flota desde un PDF. "
                    + "Para cada tarjeta que aparezca en el texto, extrae ÚNICAMENTE lo que aparezca "
                    + "literalmente: numeroTarjeta (el número completo de la tarjeta), alias (si aparece un "
                    + "nombre/alias asociado, si no null), codigoProveedor (el código o nombre del proveedor de "
                    + "la tarjeta tal cual aparece en el texto, en mayúsculas — ej. REPSOL, CEPSA, SOLRED). "
                    + "No inventes ni derives datos que no estén en el texto.";

    private final LlmListaExtractorService extractorService;
    private final TarjetaXlsxRowParser parser;
    private final TarjetaImportService tarjetaImportService;

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Previsualiza el alta de tarjetas extraída por IA de un PDF",
            description = "No persiste nada. Extrae filas candidatas (numeroTarjeta, alias, codigoProveedor) de "
                    + "un PDF vía IA y las valida con las mismas reglas que el import Excel (el proveedor NO se "
                    + "resuelve contra BD en esta fase: una fila válida aquí puede aun así fallar al confirmar).")
    public ResponseEntity<ImportPdfPreviewDTO> preview(@RequestPart("file") MultipartFile file) throws Exception {
        byte[] bytes = validarPdf(file);
        List<JsonNode> filasCrudas = extractorService.extraerFilas(bytes, SYSTEM_PROMPT, buildFilaSchema());
        List<FilaPreviewDTO> filas = mapearYValidar(filasCrudas);
        return ResponseEntity.ok(new ImportPdfPreviewDTO(COLUMNAS, filas));
    }

    @PostMapping("/confirmar")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Confirma el alta de tarjetas revisadas/editadas tras la previsualización PDF+IA",
            description = "Revalida cada fila con las mismas reglas del import Excel antes de persistir nada; "
                    + "el frontend no decide qué fila es válida.")
    public ResponseEntity<ImportTarjetasAltaReportDTO> confirmar(@RequestBody ConfirmarImportPdfRequest request) {
        long inicio = System.currentTimeMillis();
        int creados = 0;
        int existentes = 0;
        List<String> errores = new ArrayList<>();

        for (Map<String, String> campos : request.filas()) {
            Optional<FilaTarjetaImportada> parsed = parser.validar(
                    campos.get("numeroTarjeta"), campos.get("alias"), campos.get("codigoProveedor"));
            if (parsed.isEmpty()) continue;
            FilaTarjetaImportada fila = parsed.get();

            if (!fila.esValida()) {
                errores.add("Tarjeta " + fila.numeroTarjeta() + " — " + fila.errorValidacion());
                continue;
            }

            ResultadoFila resultado = tarjetaImportService.procesarFilaValidada(fila);
            if (resultado.error() != null) {
                errores.add("Tarjeta " + fila.numeroTarjeta() + " — " + resultado.error());
            } else if (resultado.yaExistia()) {
                existentes++;
            } else {
                creados++;
            }
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Import tarjetas PDF+IA (confirmar): creados={}, existentes={}, errores={}, duracion={}ms",
                creados, existentes, errores.size(), duracion);
        return ResponseEntity.ok(new ImportTarjetasAltaReportDTO(creados, existentes, errores, duracion));
    }

    /**
     * Mapea filas crudas devueltas por la IA a {@link FilaPreviewDTO},
     * aplicando la misma validación que el import Excel. Separado de
     * {@link #preview} para poder testearlo con {@link JsonNode} construidos
     * a mano, sin llamar a la IA real.
     */
    List<FilaPreviewDTO> mapearYValidar(List<JsonNode> filasCrudas) {
        List<FilaPreviewDTO> resultado = new ArrayList<>();
        for (JsonNode filaCruda : filasCrudas) {
            String numeroTarjeta = texto(filaCruda, "numeroTarjeta");
            String alias = texto(filaCruda, "alias");
            String codigoProveedor = texto(filaCruda, "codigoProveedor");

            Optional<FilaTarjetaImportada> parsed = parser.validar(numeroTarjeta, alias, codigoProveedor);
            if (parsed.isEmpty()) continue;
            FilaTarjetaImportada fila = parsed.get();

            Map<String, String> campos = new LinkedHashMap<>();
            campos.put("numeroTarjeta", vacioSiNull(fila.numeroTarjeta()));
            campos.put("alias", vacioSiNull(fila.alias()));
            campos.put("codigoProveedor", vacioSiNull(fila.codigoProveedor()));

            resultado.add(new FilaPreviewDTO(campos, fila.esValida(), fila.errorValidacion()));
        }
        return resultado;
    }

    private String texto(JsonNode fila, String campo) {
        JsonNode nodo = fila.get(campo);
        return (nodo == null || nodo.isNull()) ? null : nodo.asText();
    }

    private String vacioSiNull(String valor) {
        return valor == null ? "" : valor;
    }

    private byte[] validarPdf(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("El archivo es obligatorio");
        }
        if (file.getSize() > TAMANO_MAXIMO_BYTES) {
            throw new BusinessException("El archivo no puede superar 10 MB");
        }
        byte[] bytes = file.getBytes();
        if (!esPdf(bytes)) {
            throw new BusinessException("El archivo debe ser un PDF válido");
        }
        return bytes;
    }

    private boolean esPdf(byte[] bytes) {
        return bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private ObjectNode buildFilaSchema() {
        ObjectNode fila = MAPPER.createObjectNode();
        fila.put("type", "object");
        fila.put("additionalProperties", false);
        ObjectNode props = fila.putObject("properties");
        LlmListaExtractorService.nullableString(props, "numeroTarjeta");
        LlmListaExtractorService.nullableString(props, "alias");
        LlmListaExtractorService.nullableString(props, "codigoProveedor");
        fila.putArray("required").add("numeroTarjeta").add("alias").add("codigoProveedor");
        return fila;
    }
}
