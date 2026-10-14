package com.tecozam.bills.tarjeta.application;

import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.dto.ImportTarjetasAltaReportDTO;
import com.tecozam.bills.tarjeta.infrastructure.import_.FilaTarjetaImportada;
import com.tecozam.bills.tarjeta.infrastructure.import_.TarjetaXlsxRowParser;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Orquestador del alta masiva de Excel para el maestro Tarjetas — mismo
 * patron que {@code VehiculoImportService} (parser fila puro + reporte de
 * contadores/avisos), ver odd/tasks/maestros-import-masivo.md T2.
 *
 * <p>A diferencia de {@code ListadoTarjetasImportService} (import de
 * MOVIMIENTOS de un proveedor concreto, con alias SOLRED/CEPSA especificos
 * de combustible), esto es alta de tarjetas MAESTRO para cualquier
 * proveedor ya dado de alta en el sistema — por eso resuelve el proveedor
 * por su codigo real tal cual esta en BD, sin aplicar esos alias
 * especificos de combustible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TarjetaImportService {

    private final TarjetaXlsxRowParser parser;
    private final TarjetaRepository tarjetaRepository;
    private final ProveedorRepository proveedorRepository;

    @Transactional
    public ImportTarjetasAltaReportDTO importar(MultipartFile file) {
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
                Optional<FilaTarjetaImportada> parsed = parser.parse(row, headers);
                if (parsed.isEmpty()) continue;
                FilaTarjetaImportada fila = parsed.get();

                if (!fila.esValida()) {
                    errores.add("Tarjeta " + fila.numeroTarjeta() + " — " + fila.errorValidacion());
                    continue;
                }

                if (tarjetaRepository.existsByNumeroTarjeta(fila.numeroTarjeta())) {
                    existentes++;
                    continue;
                }

                Optional<Proveedor> proveedor = resolverProveedor(fila.codigoProveedor());
                if (proveedor.isEmpty()) {
                    errores.add("Tarjeta " + fila.numeroTarjeta() + " — Proveedor '"
                            + fila.codigoProveedor() + "' no registrado o inactivo");
                    continue;
                }

                tarjetaRepository.save(Tarjeta.builder()
                        .numeroTarjeta(fila.numeroTarjeta())
                        .alias(blankToNull(fila.alias()))
                        .proveedor(proveedor.get())
                        .estado(EstadoRecurso.DISPONIBLE)
                        .activa(true)
                        .build());
                creados++;
            }
        } catch (IOException e) {
            throw new BusinessException("Excel inválido o ilegible", e);
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Import tarjetas (alta): creados={}, existentes={}, errores={}, duracion={}ms",
                creados, existentes, errores.size(), duracion);
        return new ImportTarjetasAltaReportDTO(creados, existentes, errores, duracion);
    }

    private Optional<Proveedor> resolverProveedor(String codigoProveedor) {
        return proveedorRepository.findByCodigo(codigoProveedor.trim().toUpperCase(Locale.ROOT))
                .filter(Proveedor::isActivo);
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
