package com.tecozam.bills.admin.application;

import com.tecozam.bills.admin.dto.ImportRepsolReportDTO;
import com.tecozam.bills.centrocoste.domain.CentroCoste;
import com.tecozam.bills.centrocoste.infrastructure.persistence.CentroCosteRepository;
import com.tecozam.bills.factura.domain.Factura;
import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.domain.TarjetaResumen;
import com.tecozam.bills.factura.infrastructure.persistence.FacturaRepository;
import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import com.tecozam.bills.factura.infrastructure.persistence.TarjetaResumenRepository;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Importa el Excel proporcionado por Repsol (REPSOL.xlsx).
 *
 * Hojas relevantes:
 *  - DATOS MATRICULA: maestro de tarjetas/conductores.
 *  - ENERO 2026 / FEBRERO 2026 / MARZO 2026: operaciones detalladas por tarjeta.
 *
 * Se ignoran las hojas pivote (XLITROS, XPRODUCTO€, XPRODUCTOLITRO, XEUROS, XCENTROCOSTE).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepsolImportService {

    private static final String SHEET_DATOS_MATRICULA = "DATOS MATRICULA";
    private static final List<String> MONTHLY_SHEETS = List.of("ENERO 2026", "FEBRERO 2026", "MARZO 2026");

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DataFormatter FORMATTER = new DataFormatter(new Locale("es", "ES"));

    // Índices de columna en DATOS MATRICULA
    private static final int COL_DM_NUMERO_TARJETA = 0;
    private static final int COL_DM_MATRICULA = 1;
    private static final int COL_DM_NOMBRE = 2;
    private static final int COL_DM_CENTRO_COSTE = 3;
    private static final int COL_DM_DES_PRODU = 4;

    // Índices de columna en hojas mensuales (42 columnas)
    private static final int COL_OP_NOM_EMPR = 0;
    private static final int COL_OP_NIF_EMPR = 1;
    private static final int COL_OP_NUM_SERFAC = 2;
    private static final int COL_OP_NUM_FACTUR = 9;
    private static final int COL_OP_FEC_FACTUR = 10;
    private static final int COL_OP_NUMERO_TARJETA = 11;
    private static final int COL_OP_MATRICULA = 12;
    private static final int COL_OP_NOMBRE = 13;
    private static final int COL_OP_NUM_REFER = 14;
    private static final int COL_OP_FEC_OPERAC = 15;
    private static final int COL_OP_HOR_OPERAC = 16;
    private static final int COL_OP_NOM_ESTABL = 17;
    private static final int COL_OP_POB_ESTABL = 18;
    private static final int COL_OP_KILOMETROS = 19;
    private static final int COL_OP_DES_PRODU = 20;
    private static final int COL_OP_NUM_LITROS = 21;
    private static final int COL_OP_IMPORTE = 22;
    private static final int COL_OP_IVA = 23;
    private static final int COL_OP_COD_PRODU = 24;
    private static final int COL_OP_PU_LITRO = 25;
    private static final int COL_OP_DCTO_EESS = 26;
    private static final int COL_OP_BONIF_TOTAL = 27;
    private static final int COL_OP_IMP_TOTAL = 28;
    private static final int COL_OP_SIN_IVA = 29;
    private static final int COL_OP_PRECIO_LITRO = 30;

    private final CentroCosteRepository centroCosteRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final TarjetaRepository tarjetaRepository;
    private final ProveedorRepository proveedorRepository;
    private final FacturaRepository facturaRepository;
    private final TarjetaResumenRepository tarjetaResumenRepository;
    private final OperacionRepository operacionRepository;

    @Transactional
    public ImportRepsolReportDTO importExcel(InputStream inputStream) {
        long start = System.currentTimeMillis();
        List<String> errores = new ArrayList<>();

        int centrosCreados = 0, centrosExistentes = 0;
        int trabajadoresCreados = 0, trabajadoresExistentes = 0;
        int tarjetasCreadas = 0, tarjetasExistentes = 0;
        int facturasCreadas = 0;
        int operacionesCreadas = 0;

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Proveedor repsol = resolverProveedorRepsol();

            // --- 1) Procesar DATOS MATRICULA ---
            Sheet datos = workbook.getSheet(SHEET_DATOS_MATRICULA);
            if (datos == null) {
                throw new BusinessException("No se encontró la hoja '" + SHEET_DATOS_MATRICULA + "'");
            }

            // Caché para evitar consultas repetidas
            Map<String, CentroCoste> centrosCache = new HashMap<>();
            Map<String, Trabajador> trabajadoresCache = new HashMap<>();

            int firstRow = datos.getFirstRowNum() + 1; // saltar cabecera
            for (int i = firstRow; i <= datos.getLastRowNum(); i++) {
                Row row = datos.getRow(i);
                if (row == null) continue;

                String numeroTarjeta = readString(row, COL_DM_NUMERO_TARJETA);
                if (numeroTarjeta == null || numeroTarjeta.isBlank()) continue;

                try {
                    String matricula = readString(row, COL_DM_MATRICULA);
                    String nombreCompleto = readString(row, COL_DM_NOMBRE);
                    String centroRaw = readString(row, COL_DM_CENTRO_COSTE);

                    // Centro de coste
                    if (centroRaw != null && !centroRaw.isBlank()) {
                        String codigo = centroRaw.trim();
                        if (!centrosCache.containsKey(codigo)) {
                            Optional<CentroCoste> existente = centroCosteRepository.findByCodigo(codigo);
                            if (existente.isPresent()) {
                                centrosCache.put(codigo, existente.get());
                                centrosExistentes++;
                            } else {
                                CentroCoste cc = CentroCoste.builder()
                                        .codigo(codigo)
                                        .nombre(codigo)
                                        .activo(true)
                                        .build();
                                centrosCache.put(codigo, centroCosteRepository.save(cc));
                                centrosCreados++;
                            }
                        }
                    }

                    // Trabajador
                    if (nombreCompleto != null && !nombreCompleto.isBlank()) {
                        String email = slugifyEmail(nombreCompleto);
                        if (!trabajadoresCache.containsKey(email)) {
                            Optional<Trabajador> existente = trabajadorRepository.findByEmail(email);
                            if (existente.isPresent()) {
                                trabajadoresCache.put(email, existente.get());
                                trabajadoresExistentes++;
                            } else {
                                String[] partes = splitNombre(nombreCompleto);
                                Trabajador t = Trabajador.builder()
                                        .nombre(partes[0])
                                        .apellidos(partes[1])
                                        .email(email)
                                        .activo(true)
                                        .build();
                                trabajadoresCache.put(email, trabajadorRepository.save(t));
                                trabajadoresCreados++;
                            }
                        }
                    }

                    // Tarjeta
                    Optional<Tarjeta> tarjetaExistente = tarjetaRepository.findByNumeroTarjeta(numeroTarjeta.trim());
                    if (tarjetaExistente.isPresent()) {
                        tarjetasExistentes++;
                    } else {
                        Tarjeta t = Tarjeta.builder()
                                .numeroTarjeta(numeroTarjeta.trim())
                                .alias(matricula)
                                .proveedor(repsol)
                                .estado(EstadoRecurso.DISPONIBLE)
                                .activa(true)
                                .pinEncrypted("1234")
                                .build();
                        tarjetaRepository.save(t);
                        tarjetasCreadas++;
                    }
                } catch (Exception ex) {
                    String msg = "[DATOS MATRICULA fila " + (i + 1) + "] " + ex.getMessage();
                    log.warn(msg, ex);
                    errores.add(msg);
                }
            }

            // --- 2) Procesar hojas mensuales ---
            // Caché: numFactura -> Factura (creada en esta importación)
            Map<String, Factura> facturasCache = new HashMap<>();
            // Caché: facturaId|tarjetaId -> TarjetaResumen
            Map<String, TarjetaResumen> resumenesCache = new HashMap<>();
            // Caché tarjetas
            Map<String, Tarjeta> tarjetaCache = new HashMap<>();

            for (String sheetName : MONTHLY_SHEETS) {
                Sheet sheet = workbook.getSheet(sheetName);
                if (sheet == null) {
                    errores.add("Hoja no encontrada: " + sheetName);
                    continue;
                }

                int firstOpRow = sheet.getFirstRowNum() + 1;
                for (int i = firstOpRow; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;

                    String numRefer = readString(row, COL_OP_NUM_REFER);
                    if (numRefer == null || numRefer.isBlank()) continue;

                    try {
                        String numFactur = readString(row, COL_OP_NUM_FACTUR);
                        if (numFactur == null || numFactur.isBlank()) {
                            errores.add("[" + sheetName + " fila " + (i + 1) + "] NUM_FACTUR vacío");
                            continue;
                        }
                        numFactur = numFactur.trim();

                        // Factura (cache or create)
                        Factura factura = facturasCache.get(numFactur);
                        if (factura == null) {
                            Optional<Factura> existente = facturaRepository.findByNumFacturaAndProveedorId(numFactur, repsol.getId());
                            if (existente.isPresent()) {
                                factura = existente.get();
                            } else {
                                String fecFactur = readString(row, COL_OP_FEC_FACTUR);
                                LocalDate fecha = parseDate(fecFactur);
                                LocalDate periodoDesde = fecha != null ? fecha.withDayOfMonth(1) : null;
                                LocalDate periodoHasta = fecha != null
                                        ? fecha.withDayOfMonth(fecha.lengthOfMonth())
                                        : null;

                                factura = Factura.builder()
                                        .proveedor(repsol)
                                        .numFactura(numFactur)
                                        .fecha(fecha)
                                        .periodoDesde(periodoDesde)
                                        .periodoHasta(periodoHasta)
                                        .nifCliente(readString(row, COL_OP_NIF_EMPR))
                                        .nombreCliente(readString(row, COL_OP_NOM_EMPR))
                                        .build();
                                factura = facturaRepository.save(factura);
                                facturasCreadas++;
                            }
                            facturasCache.put(numFactur, factura);
                        }

                        // Tarjeta
                        String numTarjetaRaw = readString(row, COL_OP_NUMERO_TARJETA);
                        if (numTarjetaRaw == null || numTarjetaRaw.isBlank()) {
                            errores.add("[" + sheetName + " fila " + (i + 1) + "] NUMERO TARJETA vacío");
                            continue;
                        }
                        String numTarjeta = numTarjetaRaw.trim();

                        Tarjeta tarjeta = tarjetaCache.get(numTarjeta);
                        if (tarjeta == null) {
                            tarjeta = tarjetaRepository.findByNumeroTarjeta(numTarjeta).orElse(null);
                            if (tarjeta == null) {
                                // Tarjeta no estaba en DATOS MATRICULA: crearla mínima
                                tarjeta = Tarjeta.builder()
                                        .numeroTarjeta(numTarjeta)
                                        .alias(readString(row, COL_OP_MATRICULA))
                                        .proveedor(repsol)
                                        .estado(EstadoRecurso.DISPONIBLE)
                                        .activa(true)
                                        .pinEncrypted("1234")
                                        .build();
                                tarjeta = tarjetaRepository.save(tarjeta);
                                tarjetasCreadas++;
                            }
                            tarjetaCache.put(numTarjeta, tarjeta);
                        }

                        // TarjetaResumen (uno por (Factura, Tarjeta))
                        String resumenKey = factura.getId() + "|" + tarjeta.getId();
                        TarjetaResumen resumen = resumenesCache.get(resumenKey);
                        if (resumen == null) {
                            resumen = TarjetaResumen.builder()
                                    .factura(factura)
                                    .tarjeta(tarjeta)
                                    .numTarjeta(numTarjeta)
                                    .alias(readString(row, COL_OP_MATRICULA))
                                    .conductor(readString(row, COL_OP_NOMBRE))
                                    .build();
                            resumen = tarjetaResumenRepository.save(resumen);
                            resumenesCache.put(resumenKey, resumen);
                        }

                        // Operación
                        LocalDate fecOperac = parseDate(readString(row, COL_OP_FEC_OPERAC));
                        LocalTime horOperac = parseHora(readString(row, COL_OP_HOR_OPERAC));
                        LocalDateTime fechaHora = (fecOperac != null)
                                ? fecOperac.atTime(horOperac != null ? horOperac : LocalTime.MIDNIGHT)
                                : null;

                        Operacion operacion = Operacion.builder()
                                .factura(factura)
                                .tarjetaResumen(resumen)
                                .referencia(numRefer.trim())
                                .conceptoOriginal(readString(row, COL_OP_DES_PRODU))
                                .establecimiento(buildEstablecimiento(
                                        readString(row, COL_OP_NOM_ESTABL),
                                        readString(row, COL_OP_POB_ESTABL)))
                                .fechaHora(fechaHora)
                                .kms(parseInteger(readString(row, COL_OP_KILOMETROS)))
                                .cantidad(parseBigDecimal(readString(row, COL_OP_NUM_LITROS)))
                                .precioUnitario(parseSpanishDecimal(readString(row, COL_OP_PU_LITRO)))
                                .importe(parseBigDecimal(readString(row, COL_OP_SIN_IVA)))
                                .importeTotal(parseBigDecimal(readString(row, COL_OP_IMP_TOTAL)))
                                .bonificacion(parseBigDecimal(readString(row, COL_OP_BONIF_TOTAL)))
                                .build();
                        operacionRepository.save(operacion);
                        operacionesCreadas++;
                    } catch (Exception ex) {
                        String msg = "[" + sheetName + " fila " + (i + 1) + "] " + ex.getMessage();
                        log.warn(msg, ex);
                        errores.add(msg);
                    }
                }
            }
        } catch (IOException e) {
            throw new BusinessException("Error leyendo el Excel: " + e.getMessage(), e);
        }

        long duracionMs = System.currentTimeMillis() - start;
        return new ImportRepsolReportDTO(
                centrosCreados,
                centrosExistentes,
                trabajadoresCreados,
                trabajadoresExistentes,
                tarjetasCreadas,
                tarjetasExistentes,
                facturasCreadas,
                operacionesCreadas,
                errores,
                duracionMs
        );
    }

    // ---------- helpers ----------

    private Proveedor resolverProveedorRepsol() {
        List<Proveedor> todos = proveedorRepository.findAll();
        return todos.stream()
                .filter(p -> p.getNombre() != null && p.getNombre().toLowerCase(Locale.ROOT).contains("repsol"))
                .findFirst()
                .orElseGet(() -> {
                    if (todos.isEmpty()) {
                        throw new BusinessException("No hay proveedores en la base de datos");
                    }
                    log.warn("Proveedor 'Repsol' no encontrado, usando el primero: {}", todos.get(0).getNombre());
                    return todos.get(0);
                });
    }

    private static String readString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        String value = FORMATTER.formatCellValue(cell);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static LocalDate parseDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.isBlank()) return null;
        try {
            return LocalDate.parse(yyyymmdd.trim(), DATE_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalTime parseHora(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return null;
        try {
            String padded = String.format("%04d", Integer.parseInt(hhmm.trim()));
            int h = Integer.parseInt(padded.substring(0, 2));
            int m = Integer.parseInt(padded.substring(2, 4));
            if (h < 0 || h > 23 || m < 0 || m > 59) return null;
            return LocalTime.of(h, m);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.parseInt(value.trim().replace(".", "").replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String normalized = value.trim().replace(".", "").replace(",", ".");
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parsea formato Repsol PU_LITRO "001,529" → 1.529.
     * El primer cero (o varios) son padding; la coma es decimal.
     */
    private static BigDecimal parseSpanishDecimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String normalized = value.trim().replace(",", ".");
            return new BigDecimal(normalized).stripTrailingZeros();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String buildEstablecimiento(String nombre, String poblacion) {
        if (nombre == null || nombre.isBlank()) return null;
        if (poblacion == null || poblacion.isBlank()) return nombre.trim();
        return nombre.trim() + " (" + poblacion.trim() + ")";
    }

    private static String[] splitNombre(String nombreCompleto) {
        String trim = nombreCompleto.trim().replaceAll("\\s+", " ");
        int idx = trim.indexOf(' ');
        if (idx < 0) {
            return new String[]{trim, ""};
        }
        return new String[]{trim.substring(0, idx), trim.substring(idx + 1)};
    }

    private static String slugifyEmail(String nombreCompleto) {
        String base = Normalizer.normalize(nombreCompleto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".")
                .replaceAll("^\\.+|\\.+$", "");
        if (base.isBlank()) base = "trabajador";
        return base + "@tecozam.import";
    }
}
