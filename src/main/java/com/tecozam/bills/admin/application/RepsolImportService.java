package com.tecozam.bills.admin.application;

import com.tecozam.bills.admin.dto.ImportRepsolReportDTO;
import com.tecozam.bills.admin.dto.ImportTarjetasReportDTO;
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
import com.tecozam.bills.shared.util.NombreApellidosSplitter;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import com.tecozam.bills.viat.domain.Viat;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
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
import java.util.Set;

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

    // Claves lógicas para mapear columnas por nombre de cabecera en DATOS MATRICULA / LISTADO TARJETAS.
    private static final String KEY_NUMERO_TARJETA = "NUMERO_TARJETA";
    private static final String KEY_MATRICULA = "MATRICULA";
    private static final String KEY_NOMBRE = "NOMBRE";
    private static final String KEY_CENTRO_COSTE = "CENTRO_COSTE";
    private static final String KEY_DES_PRODU = "DES_PRODU";

    // Prefijos del número de tarjeta que identifican el tipo de recurso.
    private static final String PREFIJO_TARJETA = "0007";
    private static final Set<String> PREFIJOS_VIAT = Set.of("0972", "7076", "7080");

    // Índices de columna en hojas mensuales (42 columnas) — siguen siendo fijos.
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
    private final ViatRepository viatRepository;
    private final ProveedorRepository proveedorRepository;
    private final FacturaRepository facturaRepository;
    private final TarjetaResumenRepository tarjetaResumenRepository;
    private final OperacionRepository operacionRepository;

    @Transactional
    public ImportRepsolReportDTO importExcel(InputStream inputStream) {
        long start = System.currentTimeMillis();
        List<String> errores = new ArrayList<>();
        TarjetasImportCounters counters = new TarjetasImportCounters();
        int facturasCreadas = 0;
        int operacionesCreadas = 0;

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Proveedor repsol = resolverProveedorRepsol();

            // --- 1) Procesar DATOS MATRICULA ---
            Sheet datos = workbook.getSheet(SHEET_DATOS_MATRICULA);
            if (datos == null) {
                throw new BusinessException("No se encontró la hoja '" + SHEET_DATOS_MATRICULA + "'");
            }

            Map<String, CentroCoste> centrosCache = new HashMap<>();
            Map<String, Trabajador> trabajadoresCache = new HashMap<>();
            Map<String, Viat> viatsCache = new HashMap<>();
            procesarHojaTarjetas(datos, repsol, centrosCache, trabajadoresCache, viatsCache,
                    counters, errores, SHEET_DATOS_MATRICULA);

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
                                counters.tarjetasCreadas++;
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
                counters.centrosCreados,
                counters.centrosExistentes,
                counters.trabajadoresCreados,
                counters.trabajadoresExistentes,
                counters.tarjetasCreadas,
                counters.tarjetasExistentes,
                counters.viatsCreados,
                counters.viatsExistentes,
                counters.filasIgnoradas,
                facturasCreadas,
                operacionesCreadas,
                errores,
                duracionMs
        );
    }

    /**
     * Importa el listado plano de tarjetas (LISTADO TARJETAS REPSOL.xlsx).
     * Estructura idéntica a la hoja DATOS MATRICULA del Excel maestro,
     * pero como Excel independiente con una sola hoja.
     */
    @Transactional
    public ImportTarjetasReportDTO importTarjetasListado(InputStream inputStream) {
        long start = System.currentTimeMillis();
        List<String> errores = new ArrayList<>();
        TarjetasImportCounters counters = new TarjetasImportCounters();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BusinessException("El Excel no contiene ninguna hoja");
            }
            Sheet sheet = workbook.getSheetAt(0);
            String sheetLabel = sheet.getSheetName();

            Proveedor repsol = resolverProveedorRepsol();
            Map<String, CentroCoste> centrosCache = new HashMap<>();
            Map<String, Trabajador> trabajadoresCache = new HashMap<>();
            Map<String, Viat> viatsCache = new HashMap<>();
            procesarHojaTarjetas(sheet, repsol, centrosCache, trabajadoresCache, viatsCache,
                    counters, errores, sheetLabel);
        } catch (IOException e) {
            throw new BusinessException("Error leyendo el Excel: " + e.getMessage(), e);
        }

        long duracionMs = System.currentTimeMillis() - start;
        return new ImportTarjetasReportDTO(
                counters.centrosCreados,
                counters.centrosExistentes,
                counters.trabajadoresCreados,
                counters.trabajadoresExistentes,
                counters.tarjetasCreadas,
                counters.tarjetasExistentes,
                counters.viatsCreados,
                counters.viatsExistentes,
                counters.filasIgnoradas,
                errores,
                duracionMs
        );
    }

    /**
     * Procesa una hoja con estructura de listado de tarjetas Repsol:
     * NUMERO_TARJETA | MATRICULA (alias) | NOMBRE | CENTRO_COSTE | DES_PRODU.
     *
     * Cambios v1.0.6 (TecoZam):
     *  - Lee las columnas por NOMBRE DE CABECERA (no por índice fijo).
     *  - Detecta si la fila corresponde a una Tarjeta (combustible) o un Viat
     *    (telepeaje) según el prefijo del número.
     *  - Trabajadores sin email real se crean con email = null.
     *  - Duplicados de Trabajador se detectan por nombre+apellidos.
     */
    private void procesarHojaTarjetas(
            Sheet sheet,
            Proveedor repsol,
            Map<String, CentroCoste> centrosCache,
            Map<String, Trabajador> trabajadoresCache,
            Map<String, Viat> viatsCache,
            TarjetasImportCounters counters,
            List<String> errores,
            String sheetLabel
    ) {
        // BUG 1: leemos por nombre de cabecera porque el Excel del cliente añade
        // columnas intermedias (p.ej. MATRICULA2) y el orden fijo dejaba de funcionar.
        Map<String, Integer> columnas = mapearColumnas(sheet);
        if (!columnas.containsKey(KEY_NUMERO_TARJETA) || !columnas.containsKey(KEY_NOMBRE)) {
            errores.add("[" + sheetLabel + "] Faltan columnas obligatorias en la cabecera "
                    + "(se requieren NUMERO TARJETA y NOMBRE). No se procesa la hoja.");
            return;
        }

        int colNumeroTarjeta = columnas.get(KEY_NUMERO_TARJETA);
        int colNombre = columnas.get(KEY_NOMBRE);
        Integer colMatricula = columnas.get(KEY_MATRICULA);
        Integer colCentroCoste = columnas.get(KEY_CENTRO_COSTE);

        int firstRow = sheet.getFirstRowNum() + 1; // saltar cabecera
        for (int i = firstRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            String numeroTarjeta = readString(row, colNumeroTarjeta);
            if (numeroTarjeta == null || numeroTarjeta.isBlank()) continue;
            numeroTarjeta = numeroTarjeta.trim();

            try {
                String matricula = colMatricula != null ? readString(row, colMatricula) : null;
                String nombreCompleto = readString(row, colNombre);
                String centroRaw = colCentroCoste != null ? readString(row, colCentroCoste) : null;

                // Centro de coste (opcional)
                if (centroRaw != null && !centroRaw.isBlank()) {
                    String codigo = centroRaw.trim();
                    if (!centrosCache.containsKey(codigo)) {
                        Optional<CentroCoste> existente = centroCosteRepository.findByCodigo(codigo);
                        if (existente.isPresent()) {
                            centrosCache.put(codigo, existente.get());
                            counters.centrosExistentes++;
                        } else {
                            CentroCoste cc = CentroCoste.builder()
                                    .codigo(codigo)
                                    .nombre(codigo)
                                    .activo(true)
                                    .build();
                            centrosCache.put(codigo, centroCosteRepository.save(cc));
                            counters.centrosCreados++;
                        }
                    }
                }

                // Trabajador (opcional) — sin email sintético; dedupe por nombre+apellidos
                Trabajador trabajador = null;
                if (nombreCompleto != null && !nombreCompleto.isBlank()) {
                    trabajador = resolverTrabajador(nombreCompleto, trabajadoresCache, counters);
                }

                // BUG 4: TARJETA vs VIAT según el prefijo del número
                TipoRecurso tipo = detectarTipoRecurso(numeroTarjeta);
                switch (tipo) {
                    case TARJETA -> procesarTarjeta(numeroTarjeta, matricula, repsol, counters);
                    case VIAT -> procesarViat(numeroTarjeta, matricula, trabajador, viatsCache, counters);
                    case DESCONOCIDO -> {
                        // Prefijo no reconocido: lo dejamos como tarjeta por defecto, pero avisamos
                        // y lo contamos en filasIgnoradas (no encaja en ningún patrón conocido).
                        errores.add("[" + sheetLabel + " fila " + (i + 1) + "] Prefijo desconocido en '"
                                + numeroTarjeta + "'. Se crea como TARJETA por defecto.");
                        counters.filasIgnoradas++;
                        procesarTarjeta(numeroTarjeta, matricula, repsol, counters);
                    }
                }
            } catch (Exception ex) {
                String msg = "[" + sheetLabel + " fila " + (i + 1) + "] " + ex.getMessage();
                log.warn(msg, ex);
                errores.add(msg);
            }
        }
    }

    /**
     * Lee la primera fila de la hoja y construye un map clave lógica → índice.
     * El matching ignora mayúsculas, tildes, espacios sobrantes y puntuación.
     */
    private static Map<String, Integer> mapearColumnas(Sheet sheet) {
        Map<String, Integer> resultado = new HashMap<>();
        Row header = sheet.getRow(sheet.getFirstRowNum());
        if (header == null) return resultado;

        for (int c = header.getFirstCellNum(); c < header.getLastCellNum(); c++) {
            Cell cell = header.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) continue;
            String raw = FORMATTER.formatCellValue(cell);
            if (raw == null || raw.isBlank()) continue;
            String normalized = normalizarCabecera(raw);
            String key = mapearCabeceraAClave(normalized);
            if (key != null && !resultado.containsKey(key)) {
                resultado.put(key, c);
            }
        }
        return resultado;
    }

    /**
     * Normaliza un nombre de cabecera quitando tildes, puntuación, espacios
     * extra y pasándolo a mayúsculas. Ej. "Número  de Tarjeta." → "NUMERO DE TARJETA".
     */
    private static String normalizarCabecera(String raw) {
        String sinTildes = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Sustituimos cualquier cosa que no sea letra/dígito por espacio y compactamos
        String limpio = sinTildes.replaceAll("[^A-Za-z0-9]+", " ").trim();
        return limpio.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /**
     * Mapea una cabecera normalizada a una clave lógica conocida.
     * Devuelve null si no se reconoce.
     */
    private static String mapearCabeceraAClave(String normalized) {
        // Quitamos espacios para tolerar variantes pegadas tipo "NUMEROTARJETA".
        String compact = normalized.replace(" ", "");
        return switch (compact) {
            case "NUMEROTARJETA", "NUMERODETARJETA", "NUMTARJETA", "NUMDETARJETA" -> KEY_NUMERO_TARJETA;
            case "MATRICULA" -> KEY_MATRICULA;
            case "NOMBRE" -> KEY_NOMBRE;
            case "CENTROCOSTE", "CENTRODECOSTE" -> KEY_CENTRO_COSTE;
            case "DESPRODU", "PRODUCTO", "DESCRIPCIONPRODUCTO" -> KEY_DES_PRODU;
            default -> null;
        };
    }

    /**
     * Detecta tipo de recurso a partir del prefijo del número.
     *  - 0007       → TARJETA (Solred / Repsol combustible)
     *  - 0972/7076/7080 → VIAT (telepeaje)
     *  - cualquier otro → DESCONOCIDO (se trata como tarjeta y se loggea aviso)
     */
    private static TipoRecurso detectarTipoRecurso(String numero) {
        if (numero == null || numero.length() < 4) return TipoRecurso.DESCONOCIDO;
        String prefijo = numero.substring(0, 4);
        if (PREFIJO_TARJETA.equals(prefijo)) return TipoRecurso.TARJETA;
        if (PREFIJOS_VIAT.contains(prefijo)) return TipoRecurso.VIAT;
        return TipoRecurso.DESCONOCIDO;
    }

    /**
     * Crea o reutiliza un Trabajador a partir del nombre completo del Excel.
     *
     * BUG 2: Ya no se inventa email; el Trabajador se crea con email = null.
     * BUG 3: El split de nombre/apellidos respeta nombres compuestos castellanos.
     *
     * El dedupe se hace por (nombre, apellidos) — la V11 ya permite múltiples
     * trabajadores con email NULL (filtered unique index).
     * La caché interna usa "nombre|apellidos" en mayúsculas como clave.
     */
    private Trabajador resolverTrabajador(
            String nombreCompleto,
            Map<String, Trabajador> trabajadoresCache,
            TarjetasImportCounters counters
    ) {
        String[] partes = NombreApellidosSplitter.split(nombreCompleto);
        String nombre = partes[0];
        String apellidos = partes[1];
        String cacheKey = (nombre + "|" + apellidos).toUpperCase(Locale.ROOT);

        Trabajador cached = trabajadoresCache.get(cacheKey);
        if (cached != null) return cached;

        Optional<Trabajador> existente = trabajadorRepository
                .findFirstByNombreIgnoreCaseAndApellidosIgnoreCase(nombre, apellidos);
        if (existente.isPresent()) {
            trabajadoresCache.put(cacheKey, existente.get());
            counters.trabajadoresExistentes++;
            return existente.get();
        }

        Trabajador t = Trabajador.builder()
                .nombre(nombre)
                .apellidos(apellidos)
                .email(null)
                .activo(true)
                .build();
        Trabajador saved = trabajadorRepository.save(t);
        trabajadoresCache.put(cacheKey, saved);
        counters.trabajadoresCreados++;
        return saved;
    }

    /**
     * Crea o reutiliza una Tarjeta (Solred / Repsol combustible).
     */
    private void procesarTarjeta(
            String numeroTarjeta,
            String matricula,
            Proveedor repsol,
            TarjetasImportCounters counters
    ) {
        Optional<Tarjeta> existente = tarjetaRepository.findByNumeroTarjeta(numeroTarjeta);
        if (existente.isPresent()) {
            counters.tarjetasExistentes++;
            return;
        }
        Tarjeta t = Tarjeta.builder()
                .numeroTarjeta(numeroTarjeta)
                .alias(matricula)
                .proveedor(repsol)
                .estado(EstadoRecurso.DISPONIBLE)
                .activa(true)
                .pinEncrypted("1234")
                .build();
        tarjetaRepository.save(t);
        counters.tarjetasCreadas++;
    }

    /**
     * Crea o reutiliza un Viat (telepeaje). El número completo va en `codigo`,
     * el alias corto (columna MATRICULA, ej. "8197-CYB") en `numeroSerie`,
     * y el nombre del trabajador (si lo hay) en `descripcion` para contexto.
     */
    private void procesarViat(
            String numeroTarjeta,
            String matricula,
            Trabajador trabajador,
            Map<String, Viat> viatsCache,
            TarjetasImportCounters counters
    ) {
        Viat cached = viatsCache.get(numeroTarjeta);
        if (cached != null) {
            counters.viatsExistentes++;
            return;
        }

        Optional<Viat> existente = viatRepository.findByCodigo(numeroTarjeta);
        if (existente.isPresent()) {
            viatsCache.put(numeroTarjeta, existente.get());
            counters.viatsExistentes++;
            return;
        }

        String numeroSerie = (matricula != null && !matricula.isBlank()) ? matricula.trim() : null;
        String descripcion = (trabajador != null)
                ? (trabajador.getNombre() + " " + trabajador.getApellidos()).trim()
                : null;

        Viat v = Viat.builder()
                .codigo(numeroTarjeta)
                .numeroSerie(numeroSerie)
                .descripcion(descripcion)
                .estado(EstadoRecurso.DISPONIBLE)
                .activo(true)
                .build();
        Viat saved = viatRepository.save(v);
        viatsCache.put(numeroTarjeta, saved);
        counters.viatsCreados++;
    }

    /** Tipo lógico del recurso a crear según el prefijo del número. */
    private enum TipoRecurso { TARJETA, VIAT, DESCONOCIDO }

    private static class TarjetasImportCounters {
        int centrosCreados;
        int centrosExistentes;
        int trabajadoresCreados;
        int trabajadoresExistentes;
        int tarjetasCreadas;
        int tarjetasExistentes;
        int viatsCreados;
        int viatsExistentes;
        int filasIgnoradas;
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

}
