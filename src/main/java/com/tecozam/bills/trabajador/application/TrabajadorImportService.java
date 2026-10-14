package com.tecozam.bills.trabajador.application;

import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.trabajador.dto.ImportTrabajadoresReportDTO;
import com.tecozam.bills.trabajador.infrastructure.import_.FilaTrabajadorImportada;
import com.tecozam.bills.trabajador.infrastructure.import_.TrabajadorXlsxRowParser;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orquestador del import masivo de Excel para el maestro Trabajadores —
 * mismo patron que {@code VehiculoImportService} (parser fila puro + reporte
 * de contadores/avisos), ver odd/tasks/maestros-import-masivo.md T4.
 *
 * <p>La creacion/dedupe pasa por {@link TrabajadorResolver} (punto unico de
 * resolucion ya existente, usado tambien por el import de tarjetas) — este
 * servicio solo decide si la fila ya existia ANTES de llamarlo, para poder
 * contarla como "existente" en vez de "creada" (el resolver en si es
 * idempotente pero no distingue creado/reusado en su valor de retorno).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrabajadorImportService {

    private final TrabajadorXlsxRowParser parser;
    private final TrabajadorRepository trabajadorRepository;
    private final TrabajadorResolver trabajadorResolver;

    @Transactional
    public ImportTrabajadoresReportDTO importar(MultipartFile file) {
        long inicio = System.currentTimeMillis();
        validarArchivo(file);

        int creados = 0;
        int existentes = 0;
        List<String> errores = new ArrayList<>();

        try (InputStream in = file.getInputStream();
             Workbook wb = new XSSFWorkbook(in)) {
            Sheet hoja = wb.getSheetAt(0);
            Map<String, Integer> headers = parser.leerCabeceras(hoja);
            for (Row row : hoja) {
                if (row.getRowNum() == 0) continue;
                Optional<FilaTrabajadorImportada> parsed = parser.parse(row, headers);
                if (parsed.isEmpty()) continue;
                FilaTrabajadorImportada fila = parsed.get();

                if (!fila.esValida()) {
                    errores.add(describirFila(fila) + " — " + fila.errorValidacion());
                    continue;
                }

                if (yaExiste(fila)) {
                    existentes++;
                } else {
                    creados++;
                }
                trabajadorResolver.resolver(
                        fila.nombre(), fila.apellidos(), fila.email(), fila.dniNie(), fila.origen());
            }
        } catch (IOException e) {
            throw new BusinessException("Excel inválido o ilegible", e);
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Import trabajadores: creados={}, existentes={}, errores={}, duracion={}ms",
                creados, existentes, errores.size(), duracion);
        return new ImportTrabajadoresReportDTO(creados, existentes, errores, duracion);
    }

    /** Misma prioridad de deduplicacion que {@link TrabajadorResolver#resolver}
     *  (email, si no nombre+apellidos — dniNie no es consultable directo por
     *  estar cifrado en BD). */
    private boolean yaExiste(FilaTrabajadorImportada fila) {
        if (fila.email() != null && !fila.email().isBlank()
                && trabajadorRepository.existsByEmail(fila.email().trim())) {
            return true;
        }
        return trabajadorRepository
                .findFirstByNombreIgnoreCaseAndApellidosIgnoreCase(fila.nombre().trim(), fila.apellidos().trim())
                .isPresent();
    }

    private String describirFila(FilaTrabajadorImportada fila) {
        String nombre = fila.nombre() == null ? "" : fila.nombre();
        String apellidos = fila.apellidos() == null ? "" : fila.apellidos();
        String full = (nombre + " " + apellidos).trim();
        return full.isBlank() ? "Fila sin nombre ni apellidos" : full;
    }

    private void validarArchivo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("El archivo es obligatorio");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new BusinessException("El archivo no puede superar 10 MB");
        }
    }
}
