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
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
             Workbook wb = WorkbookFactory.create(in)) {
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

                ResultadoFila resultado = procesarFilaValidada(fila);
                if (resultado.error() != null) {
                    errores.add("Tarjeta " + fila.numeroTarjeta() + " — " + resultado.error());
                } else if (resultado.yaExistia()) {
                    existentes++;
                } else {
                    creados++;
                }
            }
        } catch (IOException e) {
            throw new BusinessException("Excel inválido o ilegible", e);
        }

        long duracion = System.currentTimeMillis() - inicio;
        log.info("Import tarjetas (alta): creados={}, existentes={}, errores={}, duracion={}ms",
                creados, existentes, errores.size(), duracion);
        return new ImportTarjetasAltaReportDTO(creados, existentes, errores, duracion);
    }

    /** Resultado de procesar una fila ya validada por el parser. */
    public record ResultadoFila(boolean yaExistia, String error) {
        public static ResultadoFila existente() { return new ResultadoFila(true, null); }
        public static ResultadoFila creada() { return new ResultadoFila(false, null); }
        public static ResultadoFila error(String mensaje) { return new ResultadoFila(false, mensaje); }
    }

    /**
     * Procesa una fila YA VALIDADA por el parser (cuenta como existente,
     * creada, o falla si el proveedor no resuelve). Extraido para que el
     * import PDF+IA de tarjetas pueda reusarlo tras su propia pantalla de
     * revision, sin duplicar dedupe/resolucion de proveedor/alta.
     */
    public ResultadoFila procesarFilaValidada(FilaTarjetaImportada fila) {
        if (tarjetaRepository.existsByNumeroTarjeta(fila.numeroTarjeta())) {
            return ResultadoFila.existente();
        }

        Optional<Proveedor> proveedor = resolverProveedor(fila.codigoProveedor());
        if (proveedor.isEmpty()) {
            return ResultadoFila.error("Proveedor '" + fila.codigoProveedor() + "' no registrado o inactivo");
        }

        tarjetaRepository.save(Tarjeta.builder()
                .numeroTarjeta(fila.numeroTarjeta())
                .alias(blankToNull(fila.alias()))
                .proveedor(proveedor.get())
                .estado(EstadoRecurso.DISPONIBLE)
                .activa(true)
                .build());
        return ResultadoFila.creada();
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
