package com.tecozam.bills.viat.application;

import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.viat.domain.Viat;
import com.tecozam.bills.viat.dto.ImportViatsReportDTO;
import com.tecozam.bills.viat.infrastructure.import_.FilaViatImportada;
import com.tecozam.bills.viat.infrastructure.import_.ViatXlsxRowParser;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
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
 * Orquestador del import masivo de Excel para el maestro VIATs — mismo
 * patron que {@code VehiculoImportService} (parser fila puro + reporte de
 * contadores/avisos), ver odd/tasks/maestros-import-masivo.md T3.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ViatImportService {

    private final ViatXlsxRowParser parser;
    private final ViatRepository viatRepository;

    @Transactional
    public ImportViatsReportDTO importar(MultipartFile file) {
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
                Optional<FilaViatImportada> parsed = parser.parse(row, headers);
                if (parsed.isEmpty()) continue;
                FilaViatImportada fila = parsed.get();

                if (!fila.esValida()) {
                    errores.add(describirFila(fila) + " — " + fila.errorValidacion());
                    continue;
                }

                if (viatRepository.existsByCodigo(fila.codigo())) {
                    existentes++;
                    continue;
                }

                viatRepository.save(Viat.builder()
                        .codigo(fila.codigo())
                        .numeroSerie(blankToNull(fila.numeroSerie()))
                        .descripcion(blankToNull(fila.descripcion()))
                        .estado(EstadoRecurso.DISPONIBLE)
                        .activo(true)
                        .build());
                creados++;
            }
        } catch (IOException e) {
            throw new BusinessException("Excel inválido o ilegible", e);
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Import viats: creados={}, existentes={}, errores={}, duracion={}ms",
                creados, existentes, errores.size(), duracion);
        return new ImportViatsReportDTO(creados, existentes, errores, duracion);
    }

    private String describirFila(FilaViatImportada fila) {
        if (fila.codigo() != null && !fila.codigo().isBlank()) return "Codigo " + fila.codigo();
        return "Fila sin codigo";
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
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
