package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.dto.CrearUsuarioCampoRequest;
import com.tecozam.bills.auth.dto.CrearUsuarioOficinaRequest;
import com.tecozam.bills.auth.dto.ImportUsuariosReportDTO;
import com.tecozam.bills.auth.infrastructure.import_.FilaUsuarioImportada;
import com.tecozam.bills.auth.infrastructure.import_.UsuarioXlsxRowParser;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
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
 * Orquestador del import masivo de Excel para Usuarios (oficina y campo en
 * el mismo archivo, columna Tipo) — mismo patron que
 * {@code VehiculoImportService}, ver odd/tasks/maestros-import-masivo.md T5.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsuarioImportService {

    private final UsuarioXlsxRowParser parser;
    private final UsuarioOficinaRepository usuarioOficinaRepository;
    private final UsuarioCampoRepository usuarioCampoRepository;
    private final UsuarioOficinaService usuarioOficinaService;
    private final UsuarioCampoService usuarioCampoService;

    @Transactional
    public ImportUsuariosReportDTO importar(MultipartFile file) {
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
                Optional<FilaUsuarioImportada> parsed = parser.parse(row, headers);
                if (parsed.isEmpty()) continue;
                FilaUsuarioImportada fila = parsed.get();

                if (!fila.esValida()) {
                    errores.add("Usuario " + fila.username() + " — " + fila.errorValidacion());
                    continue;
                }

                if (existeYa(fila)) {
                    existentes++;
                    continue;
                }

                crear(fila);
                creados++;
            }
        } catch (IOException e) {
            throw new BusinessException("Excel inválido o ilegible", e);
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Import usuarios: creados={}, existentes={}, errores={}, duracion={}ms",
                creados, existentes, errores.size(), duracion);
        return new ImportUsuariosReportDTO(creados, existentes, errores, duracion);
    }

    private boolean existeYa(FilaUsuarioImportada fila) {
        return "OFICINA".equals(fila.tipo())
                ? usuarioOficinaRepository.existsByUsername(fila.username())
                : usuarioCampoRepository.existsByUsername(fila.username());
    }

    private void crear(FilaUsuarioImportada fila) {
        if ("OFICINA".equals(fila.tipo())) {
            usuarioOficinaService.crear(new CrearUsuarioOficinaRequest(
                    fila.username(), fila.password(), blankToNull(fila.email()),
                    fila.nombre(), blankToNull(fila.dni()), fila.rol()));
        } else {
            usuarioCampoService.crear(new CrearUsuarioCampoRequest(
                    fila.username(), fila.password(), fila.nombre(),
                    blankToNull(fila.apellidos()), blankToNull(fila.dni()), blankToNull(fila.telefono())));
        }
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
