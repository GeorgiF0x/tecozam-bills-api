package com.tecozam.bills.factura.infrastructure.parser;

import com.tecozam.bills.factura.domain.Factura;
import com.tecozam.bills.factura.domain.FacturaConceptoResumen;
import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.domain.TarjetaResumen;
import com.tecozam.bills.shared.domain.enums.ConceptoUnificado;
import com.tecozam.bills.shared.util.ConceptoMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser para facturas REPSOL / SOLRED en formato PDF.
 *
 * Extrae cabecera, conceptos resumen, resúmenes por tarjeta y operaciones
 * detalladas usando los patrones reales observados en los PDFs.
 */
@Component
@Slf4j
public class RepsolFacturaParser implements FacturaParser {

    // ── Cabecera ──────────────────────────────────────────────────────────────
    private static final Pattern P_NUM_FACTURA   = Pattern.compile("N[úu]m\\.?\\s*Factura\\s+(.+)");
    private static final Pattern P_PERIODO       = Pattern.compile("Fecha de operaci[oó]n\\s+(\\d{2}/\\d{2}/\\d{4})\\s+AL\\s+(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern P_NUM_CUENTA    = Pattern.compile("N[úu]m\\.?\\s*de\\s*Cuenta\\s+(\\d+)");
    private static final Pattern P_NIF           = Pattern.compile("NIF\\s+(ES[A-Z0-9]+)");
    private static final Pattern P_IBAN          = Pattern.compile("IBAN[:\\s]+(ES\\d{2}[\\s\\d*]+)");
    private static final Pattern P_VENCIMIENTO   = Pattern.compile("VENCIMIENTO[:\\s]+(\\d{2}/\\d{2}/\\d{4})");
    // Admite tanto "en Euros" (ES) como "em Euros" (PT). Captura TODA la
    // tirada de tokens numéricos finales: las facturas PT observadas traen 4
    // tokens (un token líder de descuento antes de base/cuota/total), frente
    // a los 3 tokens habituales de las facturas ES. Nos quedamos con los
    // ÚLTIMOS 3 tokens de la tirada, descartando cualquier sobrante inicial.
    private static final Pattern P_TOTAL_FACTURA = Pattern.compile(
            "Total\\s+Factura\\s+e[nm]\\s+Euros\\s+((?:[\\d.]+,\\d{2}\\s*)+)");

    // ── Documentos de liquidación NLC (sin número de documento impreso) ────────
    // Estos documentos NO tienen línea "Núm. Factura" ni "Total Factura en/em
    // Euros" (no llevan desglose de IVA). Se detectan por "Total en Euros"
    // (sin la palabra "Factura" entre "Total" y "en") junto con la ausencia
    // de la línea de número de factura estándar.
    private static final Pattern P_TOTAL_LIQUIDACION = Pattern.compile("Total\\s+en\\s+Euros\\s+([\\d.]+,\\d{2})");
    private static final Pattern P_LUGAR_FECHA = Pattern.compile("Lugar\\s+y\\s+Fecha\\s+.+?-\\s*(\\d{2}/\\d{2}/\\d{4})");
    // Numero real del documento de liquidacion, cuando lo trae impreso (no
    // todos los ejemplares lo traen — de ahi el fallback sintetico de mas
    // abajo, pero cuando SI esta presente tiene prioridad).
    private static final Pattern P_NUM_DOC_LIQ = Pattern.compile("N[úu]m\\.?\\s*Doc\\.?\\s*Liq\\.?\\s+(\\S+)");

    // ── Conceptos resumen ─────────────────────────────────────────────────────
    // Cabecera real: "Concepto Cantidad Base Tipo Cuota Importe" (orden:
    // cantidad, base imponible, tipo IVA, cuota IVA, importe). La cantidad
    // (litros) es opcional: servicios como peajes/lubricantes no la traen.
    // El tipo real varía entre 21%/10%/4% (ES) y también 23% (tasa estándar
    // portuguesa); los importes admiten signo negativo (filas DESCUENTO).
    private static final Pattern P_CONCEPTO_CON_CANTIDAD = Pattern.compile(
            "^(.+?)\\s+(-?[\\d.]+,\\d{2})\\s+(-?[\\d.]+,\\d{2})\\s+(4|10|21|23)%\\s+(-?[\\d.]+,\\d{2})\\s+(-?[\\d.]+,\\d{2})$");
    private static final Pattern P_CONCEPTO_SIN_CANTIDAD = Pattern.compile(
            "^(.+?)\\s+(-?[\\d.]+,\\d{2})\\s+(4|10|21|23)%\\s+(-?[\\d.]+,\\d{2})\\s+(-?[\\d.]+,\\d{2})$");
    // Fila de 2 columnas "Concepto Importe" de los documentos de liquidación:
    // no hay columnas de tipo/cuota de IVA. Solo se aplica en contexto de
    // liquidación (ver parseConceptosResumen(..., liquidacion=true)) porque,
    // sin ese contexto, coincidiría también con las últimas columnas de las
    // filas ES/PT normales (falso positivo).
    private static final Pattern P_CONCEPTO_LIQUIDACION = Pattern.compile("^(.+?)\\s+(-?[\\d.]+,\\d{2})$");

    // ── Resumen por tarjeta ───────────────────────────────────────────────────
    private static final Pattern P_IMPORTE_TARJETA = Pattern.compile("^IMPORTE(\\d{16})\\s+(.+)$");

    // ── Cabecera de bloque de tarjeta en operaciones ──────────────────────────
    // La matrícula puede traer espacio (ej. "C. GOMEZ" cuando en realidad es
    // un alias de conductor, no una matrícula real) — por eso NO se puede
    // capturar con \S+. El literal "Conductor" es la cabecera de columna que
    // SIEMPRE aparece en la linea real (verificado con volcado PDFBox), con
    // el nombre real detras solo si el conductor esta asignado; si no lo
    // esta, la linea termina justo en "Conductor" sin nada despues. El grupo
    // final es opcional para no romper variantes sin esa palabra.
    private static final Pattern P_TARJETA_HDR = Pattern.compile(
            "N[\\u00ba°]\\s*de\\s*Tarjeta\\s+([\\d ]{15,23})\\s+N[\\u00ba°]\\s*de\\s*Matr[ií]cula\\s+(.+?)(?:\\s+Conductor\\b\\s*(.*))?$");

    // ── Línea de operación ────────────────────────────────────────────────────
    private static final Pattern P_OP_LINE = Pattern.compile(
            "^(\\d{6,8})\\s+(\\d{2}/\\d{2})\\s+(\\d{2}:\\d{2})\\s+(.+)$");

    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public FacturaParseResult parse(InputStream pdfInputStream, InputStream extractoInputStream) throws Exception {
        byte[] bytes = pdfInputStream.readAllBytes();
        String text;
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(doc);
        }

        List<String> lines = Arrays.stream(text.split("\\r?\\n"))
                .map(String::strip)
                .filter(l -> !l.isBlank())
                .toList();

        Factura.FacturaBuilder facturaBuilder = Factura.builder();
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();
        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

        // ── Nivel 1: Cabecera ─────────────────────────────────────────────────
        // Los documentos de liquidación NLC no tienen "Núm. Factura" ni
        // "Total Factura en/em Euros" (no llevan desglose de IVA); se
        // detectan y se procesan por una vía distinta que genera un
        // numFactura sintético (ver esDocumentoLiquidacion/aplicarCabeceraLiquidacion).
        boolean esLiquidacion = esDocumentoLiquidacion(lines);
        try {
            if (esLiquidacion) {
                aplicarCabeceraLiquidacion(lines, facturaBuilder);
            } else {
                parseCabecera(lines, facturaBuilder);
            }
        } catch (Exception e) {
            log.warn("[Repsol] Error parseando cabecera: {}", e.getMessage());
        }

        // Necesitamos el año del período para las operaciones
        int periodoAno = extractPeriodoAno(lines);

        // ── Nivel 2: Conceptos resumen ────────────────────────────────────────
        try {
            parseConceptosResumen(lines, conceptos, esLiquidacion);
        } catch (Exception e) {
            log.warn("[Repsol] Error parseando conceptos resumen: {}", e.getMessage());
        }

        // ── Nivel 3: Resumen por tarjeta (sección IMPORTE) ────────────────────
        try {
            parseTarjetaResumenes(lines, tarjetaResumenes);
        } catch (Exception e) {
            log.warn("[Repsol] Error parseando resúmenes de tarjeta: {}", e.getMessage());
        }

        // ── Nivel 4: Operaciones detalladas ───────────────────────────────────
        try {
            parseOperaciones(lines, tarjetaResumenes, periodoAno);
        } catch (Exception e) {
            log.warn("[Repsol] Error parseando operaciones: {}", e.getMessage());
        }

        Factura factura = facturaBuilder.build();

        // Vincular conceptos y tarjetas a la factura
        conceptos.forEach(c -> c.setFactura(factura));
        factura.setConceptos(conceptos);

        tarjetaResumenes.forEach(tr -> {
            tr.setFactura(factura);
            tr.getOperaciones().forEach(op -> op.setFactura(factura));
        });
        factura.setTarjetaResumenes(tarjetaResumenes);

        return FacturaParseResult.builder()
                .factura(factura)
                .conceptos(conceptos)
                .tarjetaResumenes(tarjetaResumenes)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CABECERA
    // ─────────────────────────────────────────────────────────────────────────

    void parseCabecera(List<String> lines, Factura.FacturaBuilder b) {
        parseCabeceraComun(lines, b);
        for (String line : lines) {
            tryMatch(P_NUM_FACTURA, line).ifPresent(m -> b.numFactura(m.group(1)));
            tryMatch(P_TOTAL_FACTURA, line).ifPresent(m -> aplicarTotalFactura(m.group(1), b));
        }
    }

    /**
     * Campos de cabecera compartidos por facturas normales (ES/PT) y
     * documentos de liquidación NLC: período, número de cuenta, NIF, IBAN
     * y vencimiento.
     */
    private void parseCabeceraComun(List<String> lines, Factura.FacturaBuilder b) {
        for (String line : lines) {
            tryMatch(P_PERIODO, line).ifPresent(m -> {
                b.periodoDesde(LocalDate.parse(m.group(1), FMT_DATE));
                b.periodoHasta(LocalDate.parse(m.group(2), FMT_DATE));
                b.fecha(LocalDate.parse(m.group(2), FMT_DATE)); // fecha = fin del período
            });
            tryMatch(P_NUM_CUENTA, line).ifPresent(m -> b.numCuenta(m.group(1)));
            tryMatch(P_NIF, line).ifPresent(m -> b.nifCliente(m.group(1)));
            tryMatch(P_IBAN, line).ifPresent(m -> b.iban(m.group(1).strip()));
            tryMatch(P_VENCIMIENTO, line).ifPresent(m -> b.vencimiento(LocalDate.parse(m.group(1), FMT_DATE)));
        }
    }

    /**
     * Extrae base/cuota/total de la tirada de tokens numéricos capturada por
     * {@link #P_TOTAL_FACTURA}. Las facturas PT observadas traen un token
     * líder adicional (bleed-through de una columna de descuento) antes de
     * los 3 valores reales; nos quedamos con los ÚLTIMOS 3 tokens siempre,
     * lo que también reproduce el comportamiento actual para ES (3 tokens).
     */
    private void aplicarTotalFactura(String tirada, Factura.FacturaBuilder b) {
        String[] tokens = tirada.trim().split("\\s+");
        if (tokens.length < 3) return;
        String baseRaw  = tokens[tokens.length - 3];
        String cuotaRaw = tokens[tokens.length - 2];
        String totalRaw = tokens[tokens.length - 1];
        b.baseImponible(parseAmount(baseRaw));
        b.totalIva(parseAmount(cuotaRaw));
        b.totalFactura(parseAmount(totalRaw));
    }

    /**
     * Detecta documentos de liquidación NLC: no tienen "Núm. Factura" (número
     * de factura estándar) y sí contienen una línea "Total en Euros" (sin la
     * palabra "Factura" entre "Total" y "en"/"em", lo que descarta las líneas
     * "Total Factura en/em Euros" de las facturas ES/PT normales).
     */
    boolean esDocumentoLiquidacion(List<String> lines) {
        boolean tieneNumFactura = lines.stream().anyMatch(l -> P_NUM_FACTURA.matcher(l).find());
        if (tieneNumFactura) return false;
        return lines.stream().anyMatch(l -> P_TOTAL_LIQUIDACION.matcher(l).find());
    }

    /**
     * Cabecera de documentos de liquidación NLC. Algunos ejemplares no traen
     * ningún número de documento impreso, así que generamos un identificador
     * SINTÉTICO a partir de la fecha de cabecera y el importe total:
     * "LIQ-{ddMMyyyy}-{total}" — pero cuando el documento SI trae el número
     * real ("Núm. Doc. Liq. NLC260134917", verificado en factura real de
     * agosto 2026), ese tiene prioridad. No hay desglose de IVA en estos
     * documentos: baseImponible = totalFactura, totalIva = 0.
     */
    void aplicarCabeceraLiquidacion(List<String> lines, Factura.FacturaBuilder b) {
        parseCabeceraComun(lines, b);

        String fecha = null;
        String numDocLiq = null;
        String primerTotalRaw = null;
        BigDecimal totalSum = BigDecimal.ZERO;
        boolean encontroTotal = false;

        for (String line : lines) {
            if (fecha == null) {
                Matcher mFecha = P_LUGAR_FECHA.matcher(line);
                if (mFecha.find()) fecha = mFecha.group(1);
            }
            if (numDocLiq == null) {
                Matcher mDoc = P_NUM_DOC_LIQ.matcher(line);
                if (mDoc.find()) numDocLiq = mDoc.group(1);
            }
            // Cada tarjeta del extracto trae su propia línea "Total en Euros
            // X": el total real del documento es la SUMA de todas, no solo
            // la primera (bug real: con 2+ tarjetas se perdía el importe de
            // todas menos la primera — 57,26 en vez de 61,96 con 2 tarjetas).
            Matcher mTotal = P_TOTAL_LIQUIDACION.matcher(line);
            if (mTotal.find()) {
                if (primerTotalRaw == null) primerTotalRaw = mTotal.group(1);
                totalSum = totalSum.add(parseAmount(mTotal.group(1)));
                encontroTotal = true;
            }
        }

        // Fallback: la variante PT de estos documentos ("Nota de Liquidação")
        // no trae la línea "Lugar y Fecha", pero sí la línea estándar
        // "Fecha de operación {desde} AL {hasta}" (verificado en muestra real
        // NLPX/00048416). Usamos el fin de período como fecha de referencia.
        if (fecha == null) {
            for (String line : lines) {
                Matcher mPeriodo = P_PERIODO.matcher(line);
                if (mPeriodo.find()) {
                    fecha = mPeriodo.group(2);
                    break;
                }
            }
        }

        if (encontroTotal) {
            b.baseImponible(totalSum);
            b.totalIva(BigDecimal.ZERO);
            b.totalFactura(totalSum);
        }
        if (numDocLiq != null) {
            b.numFactura(numDocLiq);
        } else if (fecha != null && primerTotalRaw != null) {
            b.numFactura(generarNumFacturaSinteticoLiquidacion(fecha, primerTotalRaw));
        }
    }

    /**
     * Genera el identificador sintético "LIQ-{ddMMyyyy}-{total}" para
     * documentos de liquidación NLC, que no traen ningún número de
     * documento real impreso en el PDF.
     */
    private String generarNumFacturaSinteticoLiquidacion(String fechaDdMmYyyy, String totalRaw) {
        String fechaCompacta = fechaDdMmYyyy.replace("/", "");
        return "LIQ-" + fechaCompacta + "-" + totalRaw;
    }

    private int extractPeriodoAno(List<String> lines) {
        for (String line : lines) {
            Matcher m = P_PERIODO.matcher(line);
            if (m.find()) {
                try {
                    return LocalDate.parse(m.group(2), FMT_DATE).getYear();
                } catch (Exception ignored) { /* continúa */ }
            }
        }
        return LocalDate.now().getYear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONCEPTOS RESUMEN
    // ─────────────────────────────────────────────────────────────────────────

    void parseConceptosResumen(List<String> lines, List<FacturaConceptoResumen> conceptos) {
        parseConceptosResumen(lines, conceptos, false);
    }

    /**
     * @param liquidacion cuando es {@code true}, se procesan las filas con el
     *                    formato de 2 columnas "Concepto Importe" propio de
     *                    los documentos de liquidación NLC (sin desglose de
     *                    IVA), en lugar del formato habitual ES/PT.
     */
    void parseConceptosResumen(List<String> lines, List<FacturaConceptoResumen> conceptos, boolean liquidacion) {
        boolean inSection = false;
        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            // La sección de conceptos empieza después de la cabecera y antes de los IMPORTE7078...
            if (upper.startsWith("IMPORTE")) {
                inSection = false;
            }
            // Comparación insensible a mayúsculas: la cabecera real es
            // "Concepto Cantidad ..." (solo la inicial en mayúscula), no
            // "CONCEPTO CANTIDAD" — la comparación en mayúsculas original
            // nunca coincidía con ninguna factura real.
            if (upper.contains("CONCEPTO") && upper.contains("CANTIDAD")) {
                inSection = true;
                continue;
            }
            if (!inSection) continue;

            addConceptoRepsolSiMatchea(line, conceptos, liquidacion);
        }

        // Fallback: buscar conceptos aunque no estemos en sección marcada
        if (conceptos.isEmpty()) {
            for (String line : lines) {
                if (line.toUpperCase(Locale.ROOT).startsWith("IMPORTE")) break;
                addConceptoRepsolSiMatchea(line, conceptos, liquidacion);
            }
        }
    }

    private void addConceptoRepsolSiMatchea(String line, List<FacturaConceptoResumen> conceptos, boolean liquidacion) {
        if (liquidacion) {
            // Formato de liquidación NLC: fila de 2 columnas "Concepto Importe",
            // sin tipo/cuota de IVA. Gateado tras el flag para no coincidir
            // falsamente con las últimas columnas de las filas ES/PT normales.
            Matcher mLiq = P_CONCEPTO_LIQUIDACION.matcher(line);
            if (mLiq.matches()) {
                addConceptoLiquidacion(conceptos, mLiq.group(1), mLiq.group(2));
            }
            return;
        }
        // Con columna de cantidad (litros), p. ej. combustible
        Matcher mConCantidad = P_CONCEPTO_CON_CANTIDAD.matcher(line);
        if (mConCantidad.matches()) {
            addConceptoRepsol(conceptos, mConCantidad.group(1), mConCantidad.group(2),
                    mConCantidad.group(3), mConCantidad.group(4), mConCantidad.group(5), mConCantidad.group(6));
            return;
        }
        // Sin columna de cantidad, p. ej. peajes, lubricantes, descuentos
        Matcher mSinCantidad = P_CONCEPTO_SIN_CANTIDAD.matcher(line);
        if (mSinCantidad.matches()) {
            addConceptoRepsol(conceptos, mSinCantidad.group(1), null,
                    mSinCantidad.group(2), mSinCantidad.group(3), mSinCantidad.group(4), mSinCantidad.group(5));
        }
    }

    /**
     * Concepto de liquidación NLC: sin tipo/cuota de IVA (tipoIva=0,
     * cuotaIva=0), baseImponible = importe.
     */
    private void addConceptoLiquidacion(List<FacturaConceptoResumen> conceptos, String conceptoRaw, String importeRaw) {
        addConceptoRepsol(conceptos, conceptoRaw, null, importeRaw, "0", "0", importeRaw);
    }

    private void addConceptoRepsol(List<FacturaConceptoResumen> conceptos, String conceptoRaw,
            String cantidadRaw, String baseRaw, String tipoRaw, String cuotaRaw, String importeRaw) {
        String concepto = conceptoRaw.strip();
        if (concepto.isBlank() || concepto.equalsIgnoreCase("CONCEPTO")) return;
        ConceptoUnificado unificado = resolveConceptoRepsol(concepto);
        conceptos.add(FacturaConceptoResumen.builder()
                .conceptoOriginal(concepto)
                .conceptoUnificado(unificado.name())
                .cantidad(cantidadRaw != null ? parseAmount(cantidadRaw) : null)
                .baseImponible(parseAmount(baseRaw))
                .tipoIva(new BigDecimal(tipoRaw).setScale(2, java.math.RoundingMode.HALF_UP))
                .cuotaIva(parseAmount(cuotaRaw))
                .importe(parseAmount(importeRaw))
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESÚMENES POR TARJETA
    // ─────────────────────────────────────────────────────────────────────────

    private void parseTarjetaResumenes(List<String> lines, List<TarjetaResumen> tarjetaResumenes) {
        // Mapa para buscar TarjetaResumen por número más tarde
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = P_IMPORTE_TARJETA.matcher(line);
            if (m.matches()) {
                String numTarjeta = m.group(1);
                String alias = m.group(2).strip();

                // Detectar si el alias es matrícula (formato XX99-XXX) o conductor
                String matricula = null;
                String conductor = null;
                if (alias.matches("[A-Z0-9]{2,4}-[A-Z0-9]{2,4}")) {
                    matricula = alias;
                } else {
                    conductor = alias;
                }

                // Buscar si ya existe esta tarjeta (por número)
                Optional<TarjetaResumen> existing = tarjetaResumenes.stream()
                        .filter(tr -> numTarjeta.equals(tr.getNumTarjeta()))
                        .findFirst();

                if (existing.isEmpty()) {
                    TarjetaResumen tr = TarjetaResumen.builder()
                            .numTarjeta(numTarjeta)
                            .alias(matricula != null ? matricula : conductor)
                            .conductor(conductor)
                            .conceptos(new ArrayList<>())
                            .operaciones(new ArrayList<>())
                            .build();
                    tarjetaResumenes.add(tr);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OPERACIONES
    // ─────────────────────────────────────────────────────────────────────────

    void parseOperaciones(List<String> lines, List<TarjetaResumen> tarjetaResumenes, int periodoAno) {
        TarjetaResumen currentTarjeta = null;
        String currentMatricula = null;
        String currentConductor = null;

        // Mapa por número de tarjeta (sin espacios) para búsqueda rápida
        Map<String, TarjetaResumen> tarjetaMap = new HashMap<>();
        for (TarjetaResumen tr : tarjetaResumenes) {
            tarjetaMap.put(tr.getNumTarjeta(), tr);
        }

        for (String line : lines) {
            // Detectar cabecera de bloque de tarjeta
            Matcher hdrM = P_TARJETA_HDR.matcher(line);
            if (hdrM.find()) {
                String rawNum = hdrM.group(1).replaceAll("\\s+", "");
                currentMatricula = hdrM.group(2).strip();
                String condRaw = hdrM.group(3);
                currentConductor = (condRaw != null && !condRaw.isBlank()) ? condRaw.strip() : null;

                // Buscar o crear TarjetaResumen
                currentTarjeta = tarjetaMap.get(rawNum);
                if (currentTarjeta == null) {
                    currentTarjeta = TarjetaResumen.builder()
                            .numTarjeta(rawNum)
                            .alias(currentMatricula)
                            .conductor(currentConductor)
                            .conceptos(new ArrayList<>())
                            .operaciones(new ArrayList<>())
                            .build();
                    tarjetaResumenes.add(currentTarjeta);
                    tarjetaMap.put(rawNum, currentTarjeta);
                } else {
                    if (currentTarjeta.getConductor() == null && currentConductor != null) {
                        currentTarjeta.setConductor(currentConductor);
                    }
                }
                continue;
            }

            // Detectar línea de operación
            if (currentTarjeta == null) continue;
            Matcher opM = P_OP_LINE.matcher(line);
            if (opM.matches()) {
                try {
                    Operacion op = parseLineaOperacion(opM, periodoAno, currentTarjeta);
                    op.setTarjetaResumen(currentTarjeta);
                    currentTarjeta.getOperaciones().add(op);
                } catch (Exception e) {
                    log.warn("[Repsol] Error parseando línea de operación '{}': {}", line, e.getMessage());
                }
            }
        }
    }

    /**
     * Parsea una línea de operación Repsol.
     *
     * Formato (separado por espacios):
     * {referencia} {dd/MM} {HH:mm} {concepto...} {establecimiento...} [kms] {cantidad} {precioNeto}
     * {precioIvaInc} {precioUnitario} {precioAplicado} {importe} {dto%} {dtoCent} {bonif} {importeTotal} [obs]
     */
    private Operacion parseLineaOperacion(Matcher m, int periodoAno, TarjetaResumen tarjetaResumen) {
        String referencia = m.group(1);
        String ddMM       = m.group(2);
        String horaStr    = m.group(3);
        String rest       = m.group(4).strip();

        LocalDate fecha = parseDateDdMM(ddMM, periodoAno);
        LocalDateTime fechaHora = fecha.atTime(
                Integer.parseInt(horaStr.substring(0, 2)),
                Integer.parseInt(horaStr.substring(3, 5)));

        // Dividir el resto por espacios para extraer los campos numéricos desde el final
        String[] tokens = rest.split("\\s+");

        // Los últimos tokens son los valores numéricos.
        // Formato esperado al final: cantidad precioNeto precioIvaInc precioUnitario precioAplicado importe dto% dtoCent bonif importeTotal [obs]
        // Al menos necesitamos: cantidad(L) + ~9 valores numéricos = 10 campos numéricos mínimo
        // Estrategia: iterar desde el final recogiendo números
        List<BigDecimal> nums = new ArrayList<>();
        List<String> obsTokens = new ArrayList<>();
        int i = tokens.length - 1;

        // El último token puede ser un código de observación (T, F, TF, TFC, etc.)
        // Longitud variable: no acotamos el número de letras porque un importe real
        // nunca es puramente alfabético, así que no hay ambigüedad posible.
        String obs = null;
        if (i >= 0 && tokens[i].matches("[A-Z]+")) {
            obs = tokens[i];
            i--;
        }

        // Recoger hasta 10 valores numéricos desde el final
        while (i >= 0 && nums.size() < 10) {
            String tok = tokens[i];
            if (tok.matches("-?[\\d.]+,[\\d]+")) {
                nums.add(0, parseAmount(tok));
                i--;
            } else {
                break;
            }
        }

        // El resto antes de los números: "concepto [E.S. establecimiento] [kms]"
        // o "concepto establecimiento"
        StringBuilder conceptoSb = new StringBuilder();
        StringBuilder estabSb = new StringBuilder();
        boolean inEstab = false;
        for (int j = 0; j <= i; j++) {
            String tok = tokens[j];
            // El nombre del establecimiento a veces viene pegado a "E.S."
            // sin espacio (p.ej. "E.S.ESPINOSA"), no solo como token exacto
            // "E.S." separado — con equals() a secas esos casos se perdian
            // enteros dentro del concepto (bug real visto en factura Solred
            // "documento de liquidacion" de agosto 2026).
            if (tok.startsWith("E.S.")) {
                inEstab = true;
                String pegado = tok.substring(4);
                if (!pegado.isBlank()) {
                    if (estabSb.length() > 0) estabSb.append(" ");
                    estabSb.append(pegado);
                }
                continue;
            }
            if (inEstab) {
                if (estabSb.length() > 0) estabSb.append(" ");
                estabSb.append(tok);
            } else {
                if (conceptoSb.length() > 0) conceptoSb.append(" ");
                conceptoSb.append(tok);
            }
        }

        String conceptoOriginal = conceptoSb.toString().strip();
        String establecimiento  = estabSb.toString().strip();

        // Si no se detectó "E.S." intentamos separar por el patrón de conceptos conocidos
        if (establecimiento.isBlank() && !conceptoOriginal.isBlank()) {
            String[] partes = splitConceptoEstablecimiento(conceptoOriginal);
            conceptoOriginal  = partes[0];
            establecimiento   = partes[1];
        }

        ConceptoUnificado conceptoUnificado = resolveConceptoRepsol(conceptoOriginal);

        // Mapear valores numéricos a campos
        // nums: [cantidad, precioNeto, precioIvaInc, precioUnitario, precioAplicado, importe, dto%, dtoCent, bonif, importeTotal]
        BigDecimal cantidad       = nums.size() > 0 ? nums.get(0) : null;
        BigDecimal precioNeto     = nums.size() > 1 ? nums.get(1) : null;
        BigDecimal precioIvaInc   = nums.size() > 2 ? nums.get(2) : null;
        BigDecimal precioUnitario = nums.size() > 3 ? nums.get(3) : null;
        BigDecimal precioAplicado = nums.size() > 4 ? nums.get(4) : null;
        BigDecimal importe        = nums.size() > 5 ? nums.get(5) : null;
        BigDecimal dtoPorcentaje  = nums.size() > 6 ? nums.get(6) : null;
        BigDecimal dtoTotal       = nums.size() > 7 ? nums.get(7) : null;
        BigDecimal bonificacion   = nums.size() > 8 ? nums.get(8) : null;

        // El importe realmente cobrado es siempre el ÚLTIMO número de la línea,
        // sea cual sea el número de campos intermedios presentes: los peajes
        // (AUTOPISTAS) solo traen 2 números (precio e importe), mientras que el
        // combustible puede traer hasta 10. Indexar desde el final es el único
        // criterio que se cumple en ambos formatos.
        BigDecimal importeTotal = nums.isEmpty() ? null : nums.get(nums.size() - 1);

        // Un único número no es la cantidad, es el propio importe total.
        if (nums.size() == 1) { cantidad = null; }

        return Operacion.builder()
                .referencia(referencia)
                .fechaHora(fechaHora)
                .conceptoOriginal(conceptoOriginal)
                .conceptoUnificado(conceptoUnificado.name())
                .establecimiento(establecimiento.isBlank() ? null : establecimiento)
                .cantidad(cantidad)
                .precioNeto(precioNeto)
                .precioIvaInc(precioIvaInc)
                .precioUnitario(precioUnitario)
                .precioAplicado(precioAplicado)
                .importe(importe)
                .importeTotal(importeTotal)
                .dtoPorcentaje(dtoPorcentaje)
                .dtoTotal(dtoTotal)
                .bonificacion(bonificacion)
                .observaciones(obs)
                .build();
    }

    /**
     * Intenta separar "DIESEL E+ NEOTECH (L) E.S. NOMBRE ESTACION" en
     * concepto="DIESEL E+ NEOTECH (L)" y establecimiento="NOMBRE ESTACION".
     *
     * Heurística: el concepto termina en "(L)", en una palabra clave conocida,
     * o antes del primer token que parece un nombre propio (mayúsculas puras).
     */
    private String[] splitConceptoEstablecimiento(String combined) {
        // Separar por " E.S. " si está presente
        if (combined.contains(" E.S. ")) {
            int idx = combined.indexOf(" E.S. ");
            return new String[]{ combined.substring(0, idx).strip(), combined.substring(idx + 6).strip() };
        }
        // Termina en "(L)" → el concepto es todo hasta "(L)"
        int lParen = combined.lastIndexOf("(L)");
        if (lParen >= 0) {
            return new String[]{ combined.substring(0, lParen + 3).strip(), combined.substring(lParen + 3).strip() };
        }
        // Conceptos de una sola palabra conocida. "TIENDA" cubre las compras
        // de conveniencia de los documentos de liquidación (establecimiento
        // sin marcador "E.S.", ej. "TIENDA CAFESTORE, S.A.U. CR.A-52").
        String[] knownConcepts = { "AUTOPISTAS", "LUBRICANTES", "ADBLUE", "LAVADOS/LUBRICS.", "TIENDA" };
        for (String kc : knownConcepts) {
            if (combined.startsWith(kc)) {
                return new String[]{ kc, combined.substring(kc.length()).strip() };
            }
        }
        // Fallback: todo es concepto
        return new String[]{ combined, "" };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAPEO DE CONCEPTOS REPSOL
    // ─────────────────────────────────────────────────────────────────────────

    private ConceptoUnificado resolveConceptoRepsol(String raw) {
        if (raw == null || raw.isBlank()) return ConceptoUnificado.OTROS;
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.contains("DIESEL") || upper.contains("GASOLEO") || upper.contains("NEOTECH")
                || upper.contains("DISELNEXA") || upper.startsWith("DIESEL E+")
                || upper.contains("NEXA")) {
            return ConceptoUnificado.DIESEL;
        }
        if (upper.contains("EFITEC") || upper.contains("GASOLINA") || upper.contains("GASOIL")
                || upper.contains("SIN PLOMO")) {
            return ConceptoUnificado.GASOLINA;
        }
        if (upper.contains("ADBLUE")) return ConceptoUnificado.ADBLUE;
        if (upper.contains("AUTOPISTA") || upper.contains("PEAJE")) return ConceptoUnificado.PEAJE;
        if (upper.contains("LAVADO")) return ConceptoUnificado.LAVADO;
        if (upper.contains("LUBRIC") || upper.contains("ACEITE")) return ConceptoUnificado.LUBRICANTE;
        if (upper.contains("DESCUENTO") || upper.contains("BONIF")) return ConceptoUnificado.DESCUENTO;
        // Delegar a ConceptoMapper como fallback
        ConceptoUnificado mapped = ConceptoMapper.resolve(raw);
        return mapped;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank() || s.equals("-")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.replace(".", "").replace(",", "."));
        } catch (NumberFormatException e) {
            log.debug("[Repsol] No se pudo parsear importe '{}'", s);
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseDateDdMM(String ddMM, int year) {
        String[] parts = ddMM.split("/");
        int day   = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        // Si el mes es enero y el año del período es diciembre, puede ser año siguiente
        LocalDate candidate = LocalDate.of(year, month, day);
        return candidate;
    }

    private Optional<Matcher> tryMatch(Pattern pattern, String line) {
        Matcher m = pattern.matcher(line);
        if (m.find()) return Optional.of(m);
        return Optional.empty();
    }
}
