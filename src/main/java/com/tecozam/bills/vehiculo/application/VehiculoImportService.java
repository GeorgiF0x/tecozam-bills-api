package com.tecozam.bills.vehiculo.application;

import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.vehiculo.domain.CategoriaRecurso;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import com.tecozam.bills.vehiculo.dto.ImportVehiculosReportDTO;
import com.tecozam.bills.vehiculo.infrastructure.import_.FilaVehiculoImportada;
import com.tecozam.bills.vehiculo.infrastructure.import_.VehiculoXlsxRowParser;
import com.tecozam.bills.vehiculo.infrastructure.persistence.VehiculoRepository;
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
 * Orquestador del import masivo de Excel para el maestro Vehiculos — mismo
 * patron que {@code ListadoTarjetasImportService} (parser fila puro + reporte
 * de contadores/avisos), ver odd/tasks/maestros-import-masivo.md T1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VehiculoImportService {

    private final VehiculoXlsxRowParser parser;
    private final VehiculoRepository vehiculoRepository;

    @Transactional
    public ImportVehiculosReportDTO importar(MultipartFile file) {
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
                Optional<FilaVehiculoImportada> parsed = parser.parse(row, headers);
                if (parsed.isEmpty()) continue;
                FilaVehiculoImportada fila = parsed.get();

                if (!fila.esValida()) {
                    errores.add(describirFila(fila) + " — " + fila.errorValidacion());
                    continue;
                }

                if (existeYa(fila)) {
                    existentes++;
                    continue;
                }

                vehiculoRepository.save(Vehiculo.builder()
                        .matricula(blankToNull(fila.matricula()))
                        .codigoObra(blankToNull(fila.codigoObra()))
                        .categoria(CategoriaRecurso.valueOf(fila.categoria()))
                        .tipo(fila.tipo())
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
        log.info("Import vehiculos: creados={}, existentes={}, errores={}, duracion={}ms",
                creados, existentes, errores.size(), duracion);
        return new ImportVehiculosReportDTO(creados, existentes, errores, duracion);
    }

    private boolean existeYa(FilaVehiculoImportada fila) {
        if (fila.matricula() != null && !fila.matricula().isBlank()) {
            return vehiculoRepository.existsByMatricula(fila.matricula());
        }
        if (fila.codigoObra() != null && !fila.codigoObra().isBlank()) {
            return vehiculoRepository.existsByCodigoObra(fila.codigoObra());
        }
        return false;
    }

    private String describirFila(FilaVehiculoImportada fila) {
        if (fila.matricula() != null && !fila.matricula().isBlank()) return "Matricula " + fila.matricula();
        if (fila.codigoObra() != null && !fila.codigoObra().isBlank()) return "Codigo obra " + fila.codigoObra();
        return "Fila sin matricula ni codigo de obra";
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
