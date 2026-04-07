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
    private static final Pattern P_NUM_FACTURA   = Pattern.compile("N[úu]m\\.?\\s*Factura\\s+(\\S+)");
    private static final Pattern P_PERIODO       = Pattern.compile("Fecha de operaci[oó]n\\s+(\\d{2}/\\d{2}/\\d{4})\\s+AL\\s+(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern P_NUM_CUENTA    = Pattern.compile("N[úu]m\\.?\\s*de\\s*Cuenta\\s+(\\d+)");
    private static final Pattern P_NIF           = Pattern.compile("NIF\\s+(ES[A-Z0-9]+)");
    private static final Pattern P_IBAN          = Pattern.compile("IBAN[:\\s]+(ES\\d{2}[\\s\\d*]+)");
    private static final Pattern P_VENCIMIENTO   = Pattern.compile("VENCIMIENTO[:\\s]+(\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern P_TOTAL_FACTURA = Pattern.compile("Total\\s+Factura\\s+en\\s+Euros\\s+([\\d.]+,\\d{2})\\s+([\\d.]+,\\d{2})\\s+([\\d.]+,\\d{2})");

    // ── Conceptos resumen ─────────────────────────────────────────────────────
    private static final Pattern P_CONCEPTO_RES  = Pattern.compile(
            "^(.+?)\\s+([\\d.]+,\\d{2})\\s+21%\\s+([\\d.]+,\\d{2})\\s+([\\d.]+,\\d{2})\\s+([\\d.]+,\\d{2})$");

    // ── Resumen por tarjeta ───────────────────────────────────────────────────
    private static final Pattern P_IMPORTE_TARJETA = Pattern.compile("^IMPORTE(\\d{16})\\s+(.+)$");

    // ── Cabecera de bloque de tarjeta en operaciones ──────────────────────────
    private static final Pattern P_TARJETA_HDR = Pattern.compile(
            "N[\\u00ba°]\\s*de\\s*Tarjeta\\s+([\\d ]{15,23})\\s+N[\\u00ba°]\\s*de\\s*Matr[ií]cula\\s+(\\S+)");
    private static final Pattern P_CONDUCTOR_HDR = Pattern.compile(
            "N[\\u00ba°]\\s*de\\s*Tarjeta\\s+([\\d ]{15,23})\\s+N[\\u00ba°]\\s*de\\s*Matr[ií]cula\\s+\\S+\\s+(.+)$");

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
        try {
            parseCabecera(lines, facturaBuilder);
        } catch (Exception e) {
            log.warn("[Repsol] Error parseando cabecera: {}", e.getMessage());
        }

        // Necesitamos el año del período para las operaciones
        int periodoAno = extractPeriodoAno(lines);

        // ── Nivel 2: Conceptos resumen ────────────────────────────────────────
        try {
            parseConceptosResumen(lines, conceptos);
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

    private void parseCabecera(List<String> lines, Factura.FacturaBuilder b) {
        for (String line : lines) {
            tryMatch(P_NUM_FACTURA, line).ifPresent(m -> b.numFactura(m.group(1)));
            tryMatch(P_PERIODO, line).ifPresent(m -> {
                b.periodoDesde(LocalDate.parse(m.group(1), FMT_DATE));
                b.periodoHasta(LocalDate.parse(m.group(2), FMT_DATE));
                b.fecha(LocalDate.parse(m.group(2), FMT_DATE)); // fecha = fin del período
            });
            tryMatch(P_NUM_CUENTA, line).ifPresent(m -> b.numCuenta(m.group(1)));
            tryMatch(P_NIF, line).ifPresent(m -> b.nifCliente(m.group(1)));
            tryMatch(P_IBAN, line).ifPresent(m -> b.iban(m.group(1).strip()));
            tryMatch(P_VENCIMIENTO, line).ifPresent(m -> b.vencimiento(LocalDate.parse(m.group(1), FMT_DATE)));
            tryMatch(P_TOTAL_FACTURA, line).ifPresent(m -> {
                b.baseImponible(parseAmount(m.group(1)));
                b.totalIva(parseAmount(m.group(2)));
                b.totalFactura(parseAmount(m.group(3)));
            });
        }
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

    private void parseConceptosResumen(List<String> lines, List<FacturaConceptoResumen> conceptos) {
        boolean inSection = false;
        for (String line : lines) {
            // La sección de conceptos empieza después de la cabecera y antes de los IMPORTE7078...
            if (line.startsWith("IMPORTE")) {
                inSection = false;
            }
            if (line.contains("CONCEPTO") && line.contains("CANTIDAD")) {
                inSection = true;
                continue;
            }
            if (!inSection) continue;

            Matcher m = P_CONCEPTO_RES.matcher(line);
            if (m.matches()) {
                String conceptoRaw = m.group(1).strip();
                ConceptoUnificado unificado = resolveConceptoRepsol(conceptoRaw);
                conceptos.add(FacturaConceptoResumen.builder()
                        .conceptoOriginal(conceptoRaw)
                        .conceptoUnificado(unificado.name())
                        .cantidad(parseAmount(m.group(2)))
                        .tipoIva(new BigDecimal("21.00"))
                        .cuotaIva(parseAmount(m.group(3)))
                        .baseImponible(parseAmount(m.group(4)))
                        .importe(parseAmount(m.group(5)))
                        .build());
            }
        }

        // Fallback: buscar conceptos aunque no estemos en sección marcada
        if (conceptos.isEmpty()) {
            for (String line : lines) {
                if (line.startsWith("IMPORTE")) break;
                Matcher m = P_CONCEPTO_RES.matcher(line);
                if (m.matches()) {
                    String conceptoRaw = m.group(1).strip();
                    ConceptoUnificado unificado = resolveConceptoRepsol(conceptoRaw);
                    conceptos.add(FacturaConceptoResumen.builder()
                            .conceptoOriginal(conceptoRaw)
                            .conceptoUnificado(unificado.name())
                            .cantidad(parseAmount(m.group(2)))
                            .tipoIva(new BigDecimal("21.00"))
                            .cuotaIva(parseAmount(m.group(3)))
                            .baseImponible(parseAmount(m.group(4)))
                            .importe(parseAmount(m.group(5)))
                            .build());
                }
            }
        }
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

    private void parseOperaciones(List<String> lines, List<TarjetaResumen> tarjetaResumenes, int periodoAno) {
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
                currentConductor = null;

                // Intenta extraer conductor si está en la misma línea
                Matcher condM = P_CONDUCTOR_HDR.matcher(line);
                if (condM.find()) {
                    String condPart = condM.group(2).strip();
                    // Si hay algo después de la matrícula, es el conductor
                    if (!condPart.isBlank() && !condPart.equals(currentMatricula)) {
                        currentConductor = condPart;
                    }
                }

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

        // El último token puede ser una letra de observación (T, N, etc.)
        String obs = null;
        if (i >= 0 && tokens[i].matches("[A-Z]")) {
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
            if (tok.equals("E.S.")) {
                inEstab = true;
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
        BigDecimal importeTotal   = nums.size() > 9 ? nums.get(9) : null;

        // Si sólo hay menos campos, asignamos lo que tengamos
        if (nums.size() == 1) { importeTotal = nums.get(0); cantidad = null; }

        // Fallback: calcular importeTotal si no se pudo extraer
        if (importeTotal == null && cantidad != null && precioIvaInc != null) {
            importeTotal = cantidad.multiply(precioIvaInc).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if (importeTotal == null && importe != null) {
            importeTotal = importe;
        }

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
        // Conceptos de una sola palabra conocida
        String[] knownConcepts = { "AUTOPISTAS", "LUBRICANTES", "ADBLUE", "LAVADOS/LUBRICS." };
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
