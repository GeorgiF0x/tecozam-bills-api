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
 * Parser para facturas MOEVE / CEPSA en formato PDF.
 *
 * Procesa tanto la factura principal (cabecera + conceptos resumen)
 * como el extracto opcional (operaciones detalladas por tarjeta).
 */
@Component
@Slf4j
public class MoeveFacturaParser implements FacturaParser {

    // ── Cabecera factura principal ────────────────────────────────────────────
    private static final Pattern P_NUMERO_KW      = Pattern.compile("^NUMERO$");
    private static final Pattern P_NUMERO_INLINE  = Pattern.compile("^1-\\s*(\\S+)");
    private static final Pattern P_FECHA_KW       = Pattern.compile("^FECHA$");
    private static final Pattern P_FECHA_VTO      = Pattern.compile("FECHA\\s+VENCIMIENTO[:\\s]+(\\d{2}-\\d{2}-\\d{2})");
    private static final Pattern P_IBAN_LINE      = Pattern.compile("IBAN\\s+(ES\\d{2}[\\s\\d*]+)");
    private static final Pattern P_NUM_CUENTA     = Pattern.compile("^(708\\d{10,13})");
    private static final Pattern P_TOTAL_DOC      = Pattern.compile(
            "TOTAL\\s+DOCUMENTO\\s+EUROS\\s+([\\d.,]+)\\s+([\\d.,]+)\\s+([\\d.,]+)");

    // ── Conceptos factura principal ───────────────────────────────────────────
    // Patrón con litros: concepto litros importeBase importeBase2 21 cuotaIva total
    private static final Pattern P_CONCEPTO_CON_L = Pattern.compile(
            "^(.+?)\\s+([\\d.,]+)\\s+([\\d.,]+)\\s+([\\d.,]+)\\s+21\\s+([\\d.,]+)\\s+([\\d.,]+)$");
    // Patrón sin litros: concepto importeBase importeConIVA 21 cuotaIva total
    private static final Pattern P_CONCEPTO_SIN_L = Pattern.compile(
            "^(.+?)\\s+([\\d.,]+)\\s+([\\d.,]+)\\s+21\\s+([\\d.,]+)\\s+([\\d.,]+)$");

    // ── Extracto: número factura ──────────────────────────────────────────────
    private static final Pattern P_EXTRAC_FACTURA = Pattern.compile(
            "CORRESPONDIENTE\\s+A\\s+(\\S+)");

    // ── Extracto: bloque de tarjeta ───────────────────────────────────────────
    private static final Pattern P_TARJETA_BLOCK  = Pattern.compile(
            "\\*\\*\\*\\s*MATRICULA/TARJETA\\s+(\\S+)\\s+\\*\\*\\*\\s+(\\d+)\\s+\\*\\*\\*");

    // ── Extracto: línea de operación ─────────────────────────────────────────
    // Detecta cualquier línea que empiece con 8-11 dígitos seguidos de texto de concepto
    private static final Pattern P_OP_LINE = Pattern.compile(
            "^(\\d{8,11})\\s+(DIESEL|GASOLINA|G\\.\\s*SIN|OPTIMA|ECOBLUE|LAVADO|GAS).*");

    // ── Fecha operación en extracto ───────────────────────────────────────────
    private static final Pattern P_FECHA_OP = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern P_HORA_OP  = Pattern.compile("^(\\d{2}:\\d{2})$");

    private static final DateTimeFormatter FMT_DD_MM_YY   = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter FMT_YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public FacturaParseResult parse(InputStream pdfInputStream, InputStream extractoInputStream) throws Exception {
        // ── Factura principal ─────────────────────────────────────────────────
        byte[] facturaBytes = pdfInputStream.readAllBytes();
        String facturaText  = extractText(facturaBytes);
        List<String> facturaLines = splitLines(facturaText);

        Factura.FacturaBuilder facturaBuilder = Factura.builder();
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();

        try {
            parseCabecera(facturaLines, facturaBuilder);
        } catch (Exception e) {
            log.warn("[Moeve] Error parseando cabecera: {}", e.getMessage());
        }

        try {
            parseConceptosResumen(facturaLines, conceptos);
        } catch (Exception e) {
            log.warn("[Moeve] Error parseando conceptos resumen: {}", e.getMessage());
        }

        // ── Extracto (operaciones) ────────────────────────────────────────────
        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

        if (extractoInputStream != null) {
            try {
                byte[] extractoBytes = extractoInputStream.readAllBytes();
                // SIN sortByPosition — el extracto tiene layout lineal, sortByPosition mezcla columnas
                String extractoText  = extractText(extractoBytes, false);
                List<String> extractoLines = splitLines(extractoText);
                log.info("[Moeve] Extracto: {} líneas a procesar", extractoLines.size());
                parseExtracto(extractoLines, tarjetaResumenes);
                log.info("[Moeve] Extracto: {} tarjetaResumenes parseadas", tarjetaResumenes.size());
                int totalOps = tarjetaResumenes.stream().mapToInt(tr -> tr.getOperaciones().size()).sum();
                log.info("[Moeve] Extracto: {} operaciones totales", totalOps);
            } catch (Exception e) {
                log.warn("[Moeve] Error parseando extracto: {}", e.getMessage(), e);
            }
        }

        Factura factura = facturaBuilder.build();

        // Vincular
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
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // Número de factura: línea "NUMERO" seguida del número
            if (P_NUMERO_KW.matcher(line).matches() && i + 1 < lines.size()) {
                String next = lines.get(i + 1).strip();
                if (!next.isBlank()) {
                    b.numFactura(next);
                }
            }
            // Alternativa inline: "1- 2025-724-101112504"
            Matcher inlineM = P_NUMERO_INLINE.matcher(line);
            if (inlineM.find()) {
                b.numFactura(inlineM.group(1));
            }

            // Fecha factura: línea "FECHA" seguida del valor
            if (P_FECHA_KW.matcher(line).matches() && i + 1 < lines.size()) {
                String next = lines.get(i + 1).strip();
                try {
                    LocalDate fecha = LocalDate.parse(next, FMT_DD_MM_YY);
                    b.fecha(fecha);
                } catch (Exception ignored) { /* no es la línea correcta */ }
            }

            // IBAN
            Matcher ibanM = P_IBAN_LINE.matcher(line);
            if (ibanM.find()) {
                b.iban(ibanM.group(1).strip());
            }

            // Vencimiento
            Matcher vtoM = P_FECHA_VTO.matcher(line);
            if (vtoM.find()) {
                try {
                    b.vencimiento(LocalDate.parse(vtoM.group(1), FMT_DD_MM_YY));
                } catch (Exception ignored) { /* formato inesperado */ }
            }

            // Número de cuenta cliente (empieza con 708)
            Matcher cuentaM = P_NUM_CUENTA.matcher(line);
            if (cuentaM.find()) {
                b.numCuenta(cuentaM.group(1));
            }

            // Total documento
            Matcher totalM = P_TOTAL_DOC.matcher(line);
            if (totalM.find()) {
                b.baseImponible(parseAmount(totalM.group(1)));
                b.totalIva(parseAmount(totalM.group(2)));
                b.totalFactura(parseAmount(totalM.group(3)));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONCEPTOS RESUMEN
    // ─────────────────────────────────────────────────────────────────────────

    private void parseConceptosResumen(List<String> lines, List<FacturaConceptoResumen> conceptos) {
        boolean inSection = false;
        for (String line : lines) {
            if (line.equalsIgnoreCase("CONCEPTO") || line.contains("CONCEPTO") && line.contains("LITROS")) {
                inSection = true;
                continue;
            }
            if (line.startsWith("TOTAL DOCUMENTO EUROS")) {
                inSection = false;
            }
            if (!inSection && !line.startsWith("TOTAL DOCUMENTO EUROS")) continue;

            // Intentar patrón con litros primero
            Matcher m = P_CONCEPTO_CON_L.matcher(line);
            if (m.matches()) {
                String conceptoRaw = m.group(1).strip();
                // Descartar líneas de cabecera
                if (conceptoRaw.equalsIgnoreCase("CONCEPTO") || conceptoRaw.isBlank()) continue;
                ConceptoUnificado unificado = resolveConceptoMoeve(conceptoRaw);
                boolean esDescuento = unificado == ConceptoUnificado.DESCUENTO;
                BigDecimal base = parseAmount(m.group(3));
                conceptos.add(FacturaConceptoResumen.builder()
                        .conceptoOriginal(conceptoRaw)
                        .conceptoUnificado(unificado.name())
                        .cantidad(parseAmount(m.group(2)))
                        .baseImponible(esDescuento ? base.negate() : base)
                        .tipoIva(new BigDecimal("21.00"))
                        .cuotaIva(parseAmount(m.group(5)))
                        .importe(parseAmount(m.group(6)))
                        .build());
                continue;
            }

            // Patrón sin litros
            Matcher m2 = P_CONCEPTO_SIN_L.matcher(line);
            if (m2.matches()) {
                String conceptoRaw = m2.group(1).strip();
                if (conceptoRaw.equalsIgnoreCase("CONCEPTO") || conceptoRaw.isBlank()) continue;
                ConceptoUnificado unificado = resolveConceptoMoeve(conceptoRaw);
                boolean esDescuento = unificado == ConceptoUnificado.DESCUENTO;
                BigDecimal base = parseAmount(m2.group(2));
                conceptos.add(FacturaConceptoResumen.builder()
                        .conceptoOriginal(conceptoRaw)
                        .conceptoUnificado(unificado.name())
                        .baseImponible(esDescuento ? base.negate() : base)
                        .tipoIva(new BigDecimal("21.00"))
                        .cuotaIva(parseAmount(m2.group(4)))
                        .importe(parseAmount(m2.group(5)))
                        .build());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXTRACTO
    // ─────────────────────────────────────────────────────────────────────────

    private void parseExtracto(List<String> lines, List<TarjetaResumen> tarjetaResumenes) {
        TarjetaResumen currentTarjeta = null;
        LocalDate currentFechaOp = null;
        String currentHora = null;
        String currentEstablecimiento = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // Número de factura del extracto (ignorar, ya lo tenemos)
            if (P_EXTRAC_FACTURA.matcher(line).find()) {
                continue;
            }

            // Bloque de nueva tarjeta. NEW-11: en MOEVE el conductor NO viene en
            // el extracto (todas las líneas no numéricas son nombres de estación
            // de servicio). El conductor se infiere del maestro de tarjetas.
            Matcher tarjetaM = P_TARJETA_BLOCK.matcher(line);
            if (tarjetaM.find()) {
                String identificador = tarjetaM.group(1); // ej: 11973654S o matrícula
                String numTarjeta    = tarjetaM.group(2);

                currentTarjeta = TarjetaResumen.builder()
                        .numTarjeta(numTarjeta)
                        .alias(identificador)
                        .conceptos(new ArrayList<>())
                        .operaciones(new ArrayList<>())
                        .build();
                tarjetaResumenes.add(currentTarjeta);
                // Reset del estado por-tarjeta para no arrastrar establecimiento
                // de la tarjeta anterior.
                currentFechaOp = null;
                currentHora = null;
                currentEstablecimiento = null;
                continue;
            }

            if (currentTarjeta == null) continue;

            // Líneas de cierre de bloque que NO son operación
            if (line.startsWith("TOTAL MATRICULA")
                    || line.contains("REGISTRATE EN WWW")
                    || line.contains("GESTIONA ON LINE")
                    || line.contains("PAG.:")) {
                continue;
            }

            // Detectar línea de operación (empieza con número largo seguido de concepto)
            Matcher opM = P_OP_LINE.matcher(line);
            if (opM.find()) {
                try {
                    Operacion op = parseLineaOperacionMoeve(line, currentFechaOp, currentHora,
                            currentEstablecimiento, null);
                    op.setTarjetaResumen(currentTarjeta);
                    currentTarjeta.getOperaciones().add(op);
                } catch (Exception e) {
                    log.warn("[Moeve] Error parseando operación '{}': {}", line, e.getMessage());
                }
                // Reset para la próxima operación: el establecimiento debe ser la
                // siguiente línea no-numérica encontrada, no la anterior.
                currentFechaOp = null;
                currentHora = null;
                currentEstablecimiento = null;
                continue;
            }

            // Detectar fecha de operación (yyyy-MM-dd)
            Matcher fechaM = P_FECHA_OP.matcher(line);
            if (fechaM.find() && line.strip().matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
                try {
                    currentFechaOp = LocalDate.parse(fechaM.group(1), FMT_YYYY_MM_DD);
                } catch (Exception ignored) { /* continúa */ }
                // En algunas líneas la fecha viene precedida del establecimiento:
                // "VALLECAS-VILLAVERDE 365 MADRID  2025-12-05  07:45"
                String beforeDate = line.substring(0, fechaM.start()).strip();
                if (!beforeDate.isBlank()) {
                    currentEstablecimiento = beforeDate;
                }
                continue;
            }

            // Detectar hora (HH:mm)
            Matcher horaM = P_HORA_OP.matcher(line);
            if (horaM.matches()) {
                currentHora = line;
                continue;
            }

            // Cualquier otra línea de texto (no numérica, no fecha, no hora) es
            // el nombre de la estación de servicio de la próxima operación.
            if (!line.matches("\\d.*")) {
                if (currentEstablecimiento == null) {
                    currentEstablecimiento = trimEstablecimiento(line);
                } else if (currentEstablecimiento.length() < 100) {
                    String joined = currentEstablecimiento + " " + line;
                    currentEstablecimiento = trimEstablecimiento(joined);
                }
            }
        }
    }

    private static String trimEstablecimiento(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }

    /**
     * Parsea una línea de operación MOEVE del extracto.
     *
     * Formato: {numOp} {concepto} {litros} *{precio} {importeConIVA} {tipoDesc} {descuento} {importeFinal} [{cashback}]
     * Ejemplo: "09545003218 DIESEL STAR 53,22 *1,449 77,12 DC-EURO/LT 0,135000 7,18 69,94"
     */
    private Operacion parseLineaOperacionMoeve(String line, LocalDate fecha, String hora,
            String establecimiento, String conductor) {
        String[] tokens = line.split("\\s+");
        if (tokens.length < 2) {
            return Operacion.builder().referencia(tokens[0]).build();
        }

        String referencia = tokens[0];

        // Recoger concepto: tokens 1..N hasta que encontremos un número con coma
        StringBuilder conceptoSb = new StringBuilder();
        int i = 1;
        while (i < tokens.length) {
            String tok = tokens[i];
            if (tok.matches("\\*?[\\d]+,[\\d]+") || tok.matches("\\*[\\d.,]+")) break;
            if (conceptoSb.length() > 0) conceptoSb.append(" ");
            conceptoSb.append(tok);
            i++;
        }
        String conceptoOriginal = conceptoSb.toString().strip();
        ConceptoUnificado conceptoUnificado = resolveConceptoMoeve(conceptoOriginal);

        // Recoger valores numéricos restantes
        List<String> numTokens = new ArrayList<>();
        List<String> textTokens = new ArrayList<>();
        while (i < tokens.length) {
            String tok = tokens[i];
            String cleanTok = tok.startsWith("*") ? tok.substring(1) : tok;
            if (cleanTok.matches("[\\d]+,[\\d]+") || cleanTok.matches("[\\d]+\\.[\\d]+,[\\d]+")) {
                numTokens.add(cleanTok);
            } else {
                textTokens.add(tok); // tipo de descuento, etc.
            }
            i++;
        }

        // Mapeo: litros, precioUnitario, importeConIVA, descuento, importeFinal, [cashback]
        BigDecimal litros         = numTokens.size() > 0 ? parseAmount(numTokens.get(0)) : null;
        BigDecimal precioUnitario = numTokens.size() > 1 ? parseAmount(numTokens.get(1)) : null;
        BigDecimal importeConIVA  = numTokens.size() > 2 ? parseAmount(numTokens.get(2)) : null;
        BigDecimal descuento      = numTokens.size() > 3 ? parseAmount(numTokens.get(3)) : null;
        BigDecimal dtoTotal       = numTokens.size() > 4 ? parseAmount(numTokens.get(4)) : null;
        BigDecimal importeFinal   = numTokens.size() > 5 ? parseAmount(numTokens.get(5)) : null;

        // Si sólo hay 5 valores, el 4 es dtoTotal y el 5 no existe → importeFinal = dtoTotal, dtoTotal = descuento
        if (numTokens.size() == 5) {
            importeFinal = dtoTotal;
            dtoTotal     = descuento;
        } else if (numTokens.size() < 5 && importeConIVA != null) {
            importeFinal = importeConIVA;
        }

        // Construir fechaHora
        LocalDateTime fechaHora = null;
        if (fecha != null) {
            int hh = 0, mm = 0;
            if (hora != null && hora.contains(":")) {
                hh = Integer.parseInt(hora.substring(0, 2));
                mm = Integer.parseInt(hora.substring(3, 5));
            }
            fechaHora = fecha.atTime(hh, mm);
        }

        return Operacion.builder()
                .referencia(referencia)
                .fechaHora(fechaHora)
                .conceptoOriginal(conceptoOriginal)
                .conceptoUnificado(conceptoUnificado.name())
                .establecimiento(establecimiento)
                .observaciones(conductor)
                .cantidad(litros)
                .precioUnitario(precioUnitario)
                .precioIvaInc(importeConIVA)
                .dtoTotal(dtoTotal)
                .importeTotal(importeFinal)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAPEO DE CONCEPTOS MOEVE
    // ─────────────────────────────────────────────────────────────────────────

    private ConceptoUnificado resolveConceptoMoeve(String raw) {
        if (raw == null || raw.isBlank()) return ConceptoUnificado.OTROS;
        String upper = raw.toUpperCase(Locale.ROOT);
        if (upper.contains("DIESEL STAR") || upper.contains("DIESEL OPTIMA")
                || upper.contains("DIESEL") || upper.contains("GASOLEO")
                || upper.contains("GASÓLEO")) {
            return ConceptoUnificado.DIESEL;
        }
        if (upper.contains("G. SIN PLOMO") || upper.contains("GASOLINA")
                || upper.contains("OPTIMA 95") || upper.contains("SIN PLOMO")) {
            return ConceptoUnificado.GASOLINA;
        }
        if (upper.contains("ECOBLUE")) return ConceptoUnificado.ADBLUE;
        if (upper.contains("PEAJE") || upper.contains("AUTOPISTA")) return ConceptoUnificado.PEAJE;
        if (upper.contains("LAVADO")) return ConceptoUnificado.LAVADO;
        if (upper.contains("DESCUENTO")) return ConceptoUnificado.DESCUENTO;
        // Delegar al ConceptoMapper como fallback
        return ConceptoMapper.resolve(raw);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String extractText(byte[] bytes) throws Exception {
        return extractText(bytes, false);
    }

    private String extractText(byte[] bytes, boolean sortByPosition) throws Exception {
        try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(sortByPosition);
            return stripper.getText(doc);
        }
    }

    private List<String> splitLines(String text) {
        return Arrays.stream(text.split("\\r?\\n"))
                .map(String::strip)
                .filter(l -> !l.isBlank())
                .toList();
    }

    private BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank() || s.equals("-")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.replace(".", "").replace(",", "."));
        } catch (NumberFormatException e) {
            log.debug("[Moeve] No se pudo parsear importe '{}'", s);
            return BigDecimal.ZERO;
        }
    }

    private Optional<Matcher> tryMatch(Pattern pattern, String line) {
        Matcher m = pattern.matcher(line);
        if (m.find()) return Optional.of(m);
        return Optional.empty();
    }
}
