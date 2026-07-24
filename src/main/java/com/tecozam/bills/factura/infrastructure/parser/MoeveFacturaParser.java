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
import java.math.RoundingMode;
import java.text.Normalizer;
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
    // Palabras clave equivalentes a "NUMERO" (factura) por idioma, ya sin
    // acentos ni mayúsculas — comparar siempre contra stripAccents(...).toUpperCase(...).
    private static final Set<String> NUMERO_KEYWORDS_FOLDED = Set.of("NUMERO", "FATURA");
    private static final Pattern P_NUMERO_INLINE  = Pattern.compile("^1-\\s*(\\S+)");
    // "FECHA" (ES) / "DATA" (PT) — palabra clave seguida del valor en la línea
    // siguiente, igual que el nº de factura.
    private static final Pattern P_FECHA_KW       = Pattern.compile("^(?:FECHA|DATA)$");
    // "FECHA VENCIMIENTO" (ES) / "DATA DE VENC." o "DATA DE VENCIMENTO" (PT,
    // ambas variantes observadas). El año admite 2 o 4 dígitos indistintamente
    // del idioma (varía incluso entre facturas PT: "10-07-2026" vs "10-03-26").
    private static final Pattern P_FECHA_VTO      = Pattern.compile(
            "(?:FECHA\\s+VENCIMIENTO|DATA\\s+DE\\s+VENC(?:IMENTO)?\\.?)[:\\s]+(\\d{2}-\\d{2}-\\d{2,4})");
    private static final Pattern P_IBAN_LINE      = Pattern.compile("IBAN\\s+(ES\\d{2}[\\s\\d*]+)");
    // Número de cuenta/cliente y NIF aparecen juntos en una única línea de la
    // cabecera, p. ej. "1314831 ESB83214668" o "9033380018142 ESB83214668"
    // — el número de dígitos del código de cliente varía según el tipo de
    // cuenta, así que no se acota a un prefijo fijo.
    private static final Pattern P_CUENTA_NIF     = Pattern.compile("^(\\d+)\\s+(ES[A-Z]\\d{8})$");
    private static final Pattern P_TOTAL_DOC      = Pattern.compile(
            "TOTAL\\s+DOCUMENTO\\s+EUROS\\s+(-?[\\d.,]+)\\s+(-?[\\d.,]+)\\s+(-?[\\d.,]+)");
    // Variante Family B/PT: un único importe, moneda en singular ("EUR", no "EUROS").
    // Admite signo negativo (notas de abono).
    private static final Pattern P_TOTAL_DOC_1NUM = Pattern.compile(
            "TOTAL\\s+DOCUMENTO\\s+EUR\\b\\s+(-?[\\d.,]+)");

    // ── RESUMEN/RESUMO IVA: fila de totales ───────────────────────────────────
    // Fila completa: [MONEDA] BASE TIPO CUOTA TOTAL (4 números, moneda opcional).
    // Los importes admiten signo negativo: una nota de abono (factura en negativo)
    // imprime toda la fila en negativo, p. ej. "EUR -166,79 21,00 -35,02 -201,81".
    private static final Pattern P_RESUMEN_ROW_4 = Pattern.compile(
            "^(?:([A-Z]{3})\\s+)?(-?[\\d.,]+)\\s+(-?[\\d.,]+)\\s+(-?[\\d.,]+)\\s+(-?[\\d.,]+)$");
    // Fila con la cuota omitida (p. ej. tipo 0%): [MONEDA] BASE TIPO TOTAL (3 números).
    private static final Pattern P_RESUMEN_ROW_3 = Pattern.compile(
            "^(?:([A-Z]{3})\\s+)?(-?[\\d.,]+)\\s+(-?[\\d.,]+)\\s+(-?[\\d.,]+)$");
    private static final int RESUMEN_IVA_SCAN_WINDOW = 10;

    // ── Conceptos factura principal (Family A: tabla de 5-6 columnas) ─────────
    // Con cantidad (p. ej. litros): concepto cantidad importeBase tipoIva cuotaIva total
    // Los importes admiten signo negativo (p. ej. "DESCONTO NA FATURA -268,5366 23 -61,7634 -330,3000").
    private static final Pattern P_CONCEPTO_CON_L = Pattern.compile(
            "^(.+?)\\s+(-?[\\d.,]+)\\s+(-?[\\d.,]+)\\s+(4|10|21|23)\\s+(-?[\\d.,]+)\\s+(-?[\\d.,]+)$");
    // Sin cantidad: concepto importeBase tipoIva cuotaIva total
    private static final Pattern P_CONCEPTO_SIN_L = Pattern.compile(
            "^(.+?)\\s+(-?[\\d.,]+)\\s+(4|10|21|23)\\s+(-?[\\d.,]+)\\s+(-?[\\d.,]+)$");

    // ── Conceptos factura principal (Family B: tabla de 3-4 columnas) ─────────
    // Cada fila trae su propio tipo de IVA: concepto [cantidad] tipoIva
    // importeConIva (importeConIva puede ser negativo, p. ej. filas
    // DESCUENTO/DESCONTO). La cantidad (litros) es opcional: algunas facturas
    // reales de un solo producto sí la imprimen (p. ej. "DIESEL STAR 61,61
    // 0,00 103,01"), otras no (p. ej. "GESTION SERV.AUTOP. ESPAÑA 21,00 89,30").
    // La cantidad exige coma decimal (p. ej. "61,61") para no confundirse con
    // un número entero que forma parte del propio nombre del concepto (p. ej.
    // "GASOLINA 95" es un grado de octanaje, no "GASOLINA" + cantidad 95).
    private static final Pattern P_CONCEPTO_B_3COL = Pattern.compile(
            "^(.+?)\\s+(?:(\\d+,\\d+)\\s+)?(\\d{1,2}(?:,\\d{2})?)\\s+(-?[\\d.,]+)$");

    // ── Extracto: número factura ──────────────────────────────────────────────
    private static final Pattern P_EXTRAC_FACTURA = Pattern.compile(
            "CORRESPONDIENTE\\s+A\\s+(\\S+)");

    // ── Extracto: bloque de tarjeta (Family A: marcado con asteriscos) ────────
    // El identificador (matrícula/conductor) antes del primer "***" es
    // opcional: algunas facturas reales (p. ej. peajes/gestión de red) solo
    // traen el número de tarjeta, sin identificador ("*** MATRICULA/TARJETA
    // *** 903338001814200107 ***"). La palabra "TARJETA" se traduce a "CARTÃO"
    // en facturas portuguesas ("*** MATRÍCULA/CARTÃO *** ... ***"). El "."
    // en "MATR.CULA"/"CART.O" tolera tanto la tilde real (folded por
    // stripAccents) como el "?" literal que PDFBox extrae de algunas
    // facturas PT cuya fuente no mapea bien los caracteres acentuados.
    // Los espacios alrededor de los "***" que rodean al identificador también
    // son opcionales: las facturas de peajes AUDASA los omiten
    // ("TARJETA Y0997511S***7076460769900010 ***").
    private static final Pattern P_TARJETA_BLOCK  = Pattern.compile(
            "\\*\\*\\*\\s*MATR.CULA/(?:TARJETA|CART.O)\\s+(?:([A-Z0-9]+))?\\s*\\*\\*\\*\\s*(\\d+)\\s+\\*\\*\\*");

    // ── Extracto: bloque de tarjeta (Family B: "MATRICULA: X / PAN: Y", sin
    // asteriscos). Se aplica sobre la línea ya sin acentos ni mayúsculas para
    // reconocer también "MATRÍCULA:".
    private static final Pattern P_TARJETA_BLOCK_B = Pattern.compile(
            "MATRICULA:\\s*(\\S+)\\s*/\\s*PAN:\\s*(\\S+)");

    // ── Extracto: línea de operación (Family A) ───────────────────────────────
    // Detecta cualquier línea que empiece con 8-11 dígitos seguidos de texto de concepto
    private static final Pattern P_OP_LINE = Pattern.compile(
            "^(\\d{8,11})\\s+(DIESEL|GASOLINA|G\\.\\s*SIN|OPTIMA|ECOBLUE|LAVADO|GAS).*");

    // ── Extracto: línea de operación (Family B) ───────────────────────────────
    // El establecimiento va primero (sin dígitos iniciales), seguido de fecha
    // dd-MM-yyyy y hora en la misma línea, y después el concepto y los valores
    // numéricos. Los segundos son opcionales: las facturas ES los traen
    // (HH:mm:ss) pero las PT observadas solo traen HH:mm.
    private static final Pattern P_OP_LINE_B = Pattern.compile(
            "^(.+?)\\s+(\\d{2}-\\d{2}-\\d{4})\\s+(\\d{2}:\\d{2}(?::\\d{2})?)\\s+(.+)$");

    // ── Extracto: línea de operación (Family B) sin establecimiento en la
    // misma línea ───────────────────────────────────────────────────────────
    // Cuando el nombre de la estación es demasiado largo para una línea (p. ej.
    // "P.A. MARCO CANAVESES" + "SOALHÕES" en líneas separadas), la fecha/hora
    // y el resto de la operación caen en una TERCERA línea que empieza
    // directamente por la fecha, sin ningún prefijo de texto. P_OP_LINE_B no
    // puede reconocerla (exige texto + espacio antes de la fecha). En este
    // caso se usa el establecimiento ya acumulado en `currentEstablecimiento`
    // a partir de las líneas de texto previas.
    // Ejemplo real: "05-06-2026 16:23 GASOLEO 28,56 1,961 56,01 1,961 56,01 0,13EUR/L -3,713 52,30 23"
    private static final Pattern P_OP_LINE_B_SIN_ESTABLECIMIENTO = Pattern.compile(
            "^(\\d{2}-\\d{2}-\\d{4})\\s+(\\d{2}:\\d{2}(?::\\d{2})?)\\s+(.+)$");

    // ── Extracto: línea de operación de peaje/red (Family A) ─────────────────
    // Variante sin palabra clave de combustible (peajes/gestión de red, p. ej.
    // "USO RED PORTUGAL", peajes AUDASA): establecimiento genérico, fecha y
    // hora en la propia línea, seguidas del número de operación y el resto
    // (concepto + importe). La fecha admite "yyyy-MM-dd" (guiones, ej. gestión
    // de red) o "dd.MM.yyyy" (puntos, ej. AUDASA); la hora admite segundos
    // opcionales.
    // Ejemplos reales:
    //  "OPERACIONES GESTION SERVICIOS 2026-03-31 16:07 06401064194 USO RED PORTUGAL 1,21"
    //  "AUDASA Tui-Puxeiros 02.02.2026 05:55:00 229599532 PEAJE 3,55"
    private static final Pattern P_OP_LINE_PEAJE = Pattern.compile(
            "^(.+?)\\s+(\\d{4}-\\d{2}-\\d{2}|\\d{2}\\.\\d{2}\\.\\d{4})\\s+(\\d{2}:\\d{2}(?::\\d{2})?)\\s+(\\d+)\\s+(.+)$");

    // ── Fecha operación en extracto ───────────────────────────────────────────
    private static final Pattern P_FECHA_OP = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern P_HORA_OP  = Pattern.compile("^(\\d{2}:\\d{2})$");

    private static final DateTimeFormatter FMT_DD_MM_YY   = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter FMT_DD_MM_YYYY = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FMT_YYYY_MM_DD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FMT_DD_MM_YYYY_DOT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public FacturaParseResult parse(InputStream pdfInputStream, InputStream extractoInputStream) throws Exception {
        // ── Factura principal ─────────────────────────────────────────────────
        byte[] facturaBytes = pdfInputStream.readAllBytes();
        String facturaText  = extractText(facturaBytes);
        List<String> facturaLines = splitLines(facturaText);

        TemplateDetection detection = detectTemplate(facturaLines);
        log.info("[Moeve] Plantilla detectada: familia={}, locale={}", detection.family(), detection.locale());

        Factura.FacturaBuilder facturaBuilder = Factura.builder();
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();

        try {
            parseCabecera(facturaLines, facturaBuilder, detection.locale());
        } catch (Exception e) {
            log.warn("[Moeve] Error parseando cabecera: {}", e.getMessage());
        }

        try {
            parseConceptosResumen(facturaLines, conceptos, detection.locale());
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

        // Moeve no imprime un rango de período explícito en la cabecera (solo
        // una fecha de corte, ya capturada como "fecha"): se deriva de las
        // propias operaciones del extracto, cuando existen.
        derivarPeriodoDesdeOperaciones(tarjetaResumenes, facturaBuilder);

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

    void parseCabecera(List<String> lines, Factura.FacturaBuilder b, InvoiceLocale locale) {
        String numFactura = extractNumFactura(lines);
        if (numFactura != null) {
            b.numFactura(numFactura);
        }

        // Totales: RESUMEN/RESUMO IVA es la fuente autoritativa (decisión de
        // diseño). TOTAL DOCUMENTO sólo se usa como respaldo cuando no se
        // encuentra el bloque RESUMEN/RESUMO IVA.
        ResumenIvaTotales resumenIvaTotales = parseResumenIva(lines, locale);
        if (resumenIvaTotales != null) {
            b.baseImponible(resumenIvaTotales.baseImponible());
            b.totalIva(resumenIvaTotales.totalIva());
            b.totalFactura(resumenIvaTotales.totalFactura());
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            // Fecha factura: línea "FECHA"/"DATA" seguida del valor. El año
            // viene en 2 dígitos en facturas ES ("31-05-26") pero en 4 en las
            // PT observadas ("30-06-2026") — se prueban ambos formatos.
            if (P_FECHA_KW.matcher(line).matches() && i + 1 < lines.size()) {
                String next = lines.get(i + 1).strip();
                LocalDate fecha = parseFechaFlexible(next);
                if (fecha != null) {
                    b.fecha(fecha);
                }
            }

            // IBAN
            Matcher ibanM = P_IBAN_LINE.matcher(line);
            if (ibanM.find()) {
                b.iban(ibanM.group(1).strip());
            }

            // Vencimiento
            Matcher vtoM = P_FECHA_VTO.matcher(line);
            if (vtoM.find()) {
                LocalDate vencimiento = parseFechaFlexible(vtoM.group(1));
                if (vencimiento != null) {
                    b.vencimiento(vencimiento);
                }
            }

            // Número de cuenta cliente + NIF (misma línea)
            Matcher cuentaNifM = P_CUENTA_NIF.matcher(line);
            if (cuentaNifM.matches()) {
                b.numCuenta(cuentaNifM.group(1));
                b.nifCliente(cuentaNifM.group(2));
            }

            // Total documento: sólo respaldo si RESUMEN/RESUMO IVA no aportó totales.
            if (resumenIvaTotales == null) {
                Matcher totalM = P_TOTAL_DOC.matcher(line);
                if (totalM.find()) {
                    b.baseImponible(parseAmount(totalM.group(1)));
                    b.totalIva(parseAmount(totalM.group(2)));
                    b.totalFactura(parseAmount(totalM.group(3)));
                    continue;
                }
                Matcher total1NumM = P_TOTAL_DOC_1NUM.matcher(line);
                if (total1NumM.find()) {
                    b.totalFactura(parseAmount(total1NumM.group(1)));
                }
            }
        }
    }

    /**
     * Deriva período desde/hasta de la factura a partir de la fecha mínima y
     * máxima entre todas las operaciones del extracto (fecha desde = la más
     * antigua, fecha hasta = la más reciente). Moeve no imprime un rango de
     * fechas explícito en la cabecera de la factura, a diferencia de Repsol.
     * No hace nada si no hay operaciones con fecha.
     */
    void derivarPeriodoDesdeOperaciones(List<TarjetaResumen> tarjetaResumenes, Factura.FacturaBuilder b) {
        java.util.Optional<LocalDate> min = tarjetaResumenes.stream()
                .flatMap(tr -> tr.getOperaciones().stream())
                .map(Operacion::getFechaHora)
                .filter(java.util.Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .min(LocalDate::compareTo);
        java.util.Optional<LocalDate> max = tarjetaResumenes.stream()
                .flatMap(tr -> tr.getOperaciones().stream())
                .map(Operacion::getFechaHora)
                .filter(java.util.Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .max(LocalDate::compareTo);
        min.ifPresent(b::periodoDesde);
        max.ifPresent(b::periodoHasta);
    }

    /**
     * Totales de factura (base imponible, cuota IVA, total) derivados del
     * bloque RESUMEN/RESUMO IVA.
     */
    record ResumenIvaTotales(BigDecimal baseImponible, BigDecimal totalIva, BigDecimal totalFactura) {
    }

    /**
     * Extrae los totales de factura a partir del bloque RESUMEN/RESUMO IVA,
     * fuente autoritativa según el idioma de la factura (decisión de diseño:
     * reemplaza a la frágil línea "TOTAL DOCUMENTO EUR(OS)" como fuente
     * primaria; esa línea queda sólo como respaldo/cruce). Soporta filas con
     * o sin código de moneda y filas donde la cuota fue omitida (p. ej. tipo
     * 0%), derivando en ese caso cuota = total - base. Si el bloque agrupa
     * varias filas (una por tipo de IVA), se suman. Devuelve {@code null} si
     * no se encuentra el bloque o ninguna fila de datos coincide.
     */
    ResumenIvaTotales parseResumenIva(List<String> lines, InvoiceLocale locale) {
        String keyword = stripAccents(locale.lexicon("RESUMEN")).toUpperCase(Locale.ROOT);
        int startIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            String folded = stripAccents(lines.get(i)).toUpperCase(Locale.ROOT);
            if (folded.contains(keyword)) {
                startIndex = i;
                break;
            }
        }
        if (startIndex < 0) return null;

        BigDecimal base = BigDecimal.ZERO;
        BigDecimal cuota = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        boolean matchedAnyRow = false;

        int limit = Math.min(lines.size(), startIndex + 1 + RESUMEN_IVA_SCAN_WINDOW);
        for (int i = startIndex + 1; i < limit; i++) {
            String line = lines.get(i);

            Matcher m4 = P_RESUMEN_ROW_4.matcher(line);
            if (m4.matches()) {
                base = base.add(parseAmount(m4.group(2)));
                cuota = cuota.add(parseAmount(m4.group(4)));
                total = total.add(parseAmount(m4.group(5)));
                matchedAnyRow = true;
                continue;
            }

            Matcher m3 = P_RESUMEN_ROW_3.matcher(line);
            if (m3.matches()) {
                BigDecimal rowBase = parseAmount(m3.group(2));
                BigDecimal rowTotal = parseAmount(m3.group(4));
                base = base.add(rowBase);
                total = total.add(rowTotal);
                cuota = cuota.add(rowTotal.subtract(rowBase));
                matchedAnyRow = true;
                continue;
            }

            // Ya vimos al menos una fila de datos y esta línea no encaja:
            // fin del bloque (p. ej. línea "TOTAL DOCUMENTO...").
            if (matchedAnyRow) break;
        }

        if (!matchedAnyRow) return null;
        return new ResumenIvaTotales(base, cuota, total);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONCEPTOS RESUMEN
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Detecta la línea de cabecera de la tabla de conceptos resumen, tanto en
     * Family A ("CONCEPTO CANTIDAD IMPORTE SIN IVA ...") como en Family B ES
     * ("CONCEPTO IVA IMPORTE CON IVA") o PT ("DESCRIÇÃO QUANTIDADE VALOR COM
     * IVA"). Ninguna cabecera real contiene la palabra "LITROS". Los 2
     * caracteres tras "DESCRI" se comparan con comodín ("..") porque algunas
     * facturas PT tienen una fuente mal mapeada y PDFBox extrae "?" literal en
     * vez de "Ç"/"Ã" (mismo problema ya visto en el marcador de tarjeta).
     */
    private static boolean isConceptosHeaderLine(String line) {
        String folded = stripAccents(line).toUpperCase(Locale.ROOT);
        return folded.startsWith("CONCEPTO") || folded.matches("^DESCRI..O\\b.*");
    }

    // Palabras de cabecera de la tabla de conceptos que a veces se imprimen en
    // líneas propias, separadas del título principal (p. ej. "CONCEPTO
    // CANTIDAD" seguido de "IVA", "%", "IMPORTE", "CON IVA" cada una en su
    // propia línea). Se ignoran explícitamente para que no se confundan con
    // fragmentos de un nombre de concepto partido en varias líneas.
    private static final java.util.Set<String> CONCEPTO_HEADER_CONTINUATION_WORDS = java.util.Set.of(
            "CANTIDAD", "IVA", "%", "IMPORTE", "TIPO", "CUOTA", "BASE",
            "SIN IVA", "CON IVA", "IMPORTE SIN IVA", "IMPORTE CON IVA",
            "QUANTIDADE", "VALOR", "MOEDA", "MONEDA");

    private static boolean isConceptosHeaderContinuationLine(String line) {
        String folded = stripAccents(line).toUpperCase(Locale.ROOT).strip();
        return CONCEPTO_HEADER_CONTINUATION_WORDS.contains(folded);
    }

    /**
     * Detecta el fin de la tabla de conceptos resumen: el bloque RESUMEN/
     * RESUMO IVA o la línea TOTAL DOCUMENTO (en cualquiera de sus dos formas).
     */
    private static boolean isConceptosEndLine(String line) {
        String folded = stripAccents(line).toUpperCase(Locale.ROOT);
        return folded.contains("RESUMEN IVA") || folded.contains("RESUMO IVA")
                || folded.startsWith("TOTAL DOCUMENTO");
    }

    /**
     * Parsea la tabla de conceptos resumen para ambas familias de plantilla.
     * Family A imprime base/cuota/tipo por columna (5-6 columnas, cantidad
     * opcional); Family B/PT sólo imprime el tipo de IVA y el importe con IVA
     * por fila (3 columnas), por lo que base y cuota se derivan: base =
     * importe/(1+tipo/100), cuota = importe-base. Las filas SUBTOTAL se
     * excluyen; las filas DESCUENTO/DESCONTO conservan su signo negativo.
     */
    void parseConceptosResumen(List<String> lines, List<FacturaConceptoResumen> conceptos, InvoiceLocale locale) {
        boolean inSection = false;
        // Nombres de concepto largos se parten en varias líneas de texto antes
        // de que aparezcan sus números (p. ej. "PEAJES DE AUTOPISTAS/" +
        // "TUNELES" + "3.766,7025 21 791,0075 4.557,7100"): se acumulan aquí
        // hasta poder combinarlos con la línea de números correspondiente.
        StringBuilder pendingConcepto = new StringBuilder();

        for (String line : lines) {
            if (isConceptosHeaderLine(line)) {
                inSection = true;
                pendingConcepto.setLength(0);
                continue;
            }
            if (isConceptosEndLine(line)) {
                inSection = false;
                pendingConcepto.setLength(0);
                continue;
            }
            if (!inSection) continue;
            if (isConceptosHeaderContinuationLine(line)) continue;

            boolean usingPending = pendingConcepto.length() > 0;
            String candidateLine = usingPending ? (pendingConcepto + " " + line) : line;

            // Family A: con columna de cantidad (p. ej. litros)
            Matcher mConCantidad = P_CONCEPTO_CON_L.matcher(candidateLine);
            if (mConCantidad.matches()) {
                addConceptoFamilyA(conceptos, locale, mConCantidad.group(1), mConCantidad.group(2),
                        mConCantidad.group(3), mConCantidad.group(4), mConCantidad.group(5), mConCantidad.group(6));
                pendingConcepto.setLength(0);
                continue;
            }

            // Family A: sin columna de cantidad
            Matcher mSinCantidad = P_CONCEPTO_SIN_L.matcher(candidateLine);
            if (mSinCantidad.matches()) {
                addConceptoFamilyA(conceptos, locale, mSinCantidad.group(1), null,
                        mSinCantidad.group(2), mSinCantidad.group(3), mSinCantidad.group(4), mSinCantidad.group(5));
                pendingConcepto.setLength(0);
                continue;
            }

            // Family B: tabla de 3-4 columnas, tipo de IVA por fila, cantidad opcional
            Matcher mB = P_CONCEPTO_B_3COL.matcher(candidateLine);
            if (mB.matches()) {
                addConceptoFamilyB(conceptos, locale, mB.group(1), mB.group(2), mB.group(3), mB.group(4));
                pendingConcepto.setLength(0);
                continue;
            }

            // Ninguna coincidencia: si la línea (sin acumular) es texto puro sin
            // dígitos, es probablemente un fragmento más del nombre de concepto
            // partido en varias líneas de texto (independientemente de si ya
            // había un fragmento acumulado) — se sigue acumulando. Si tiene
            // dígitos y aun así no coincide con ningún patrón conocido (p. ej.
            // SUBTOTAL), se descarta lo acumulado.
            if (!containsDigit(line)) {
                if (pendingConcepto.length() > 0) pendingConcepto.append(" ");
                pendingConcepto.append(line);
            } else {
                pendingConcepto.setLength(0);
            }
        }
    }

    private static boolean containsDigit(String s) {
        return s.chars().anyMatch(Character::isDigit);
    }

    private void addConceptoFamilyA(List<FacturaConceptoResumen> conceptos, InvoiceLocale locale,
            String conceptoRaw, String cantidadRaw, String baseRaw, String tipoRaw, String cuotaRaw,
            String totalRaw) {
        String concepto = conceptoRaw.strip();
        if (concepto.isBlank() || concepto.equalsIgnoreCase("CONCEPTO")) return;
        int tipoEntero = Integer.parseInt(tipoRaw);
        if (!locale.isValidTipoIva(tipoEntero)) return;

        ConceptoUnificado unificado = resolveConceptoMoeve(concepto);
        conceptos.add(FacturaConceptoResumen.builder()
                .conceptoOriginal(concepto)
                .conceptoUnificado(unificado.name())
                .cantidad(cantidadRaw != null ? parseAmount(cantidadRaw) : null)
                .baseImponible(parseAmount(baseRaw))
                .tipoIva(new BigDecimal(tipoRaw).setScale(2, RoundingMode.HALF_UP))
                .cuotaIva(parseAmount(cuotaRaw))
                .importe(parseAmount(totalRaw))
                .build());
    }

    private void addConceptoFamilyB(List<FacturaConceptoResumen> conceptos, InvoiceLocale locale,
            String conceptoRaw, String cantidadRaw, String tipoRaw, String importeRaw) {
        String concepto = conceptoRaw.strip();
        if (concepto.isBlank() || concepto.equalsIgnoreCase("SUBTOTAL")) return;

        BigDecimal tipoIva = parseAmount(tipoRaw).setScale(2, RoundingMode.HALF_UP);
        int tipoEntero = tipoIva.setScale(0, RoundingMode.HALF_UP).intValue();
        if (!locale.isValidTipoIva(tipoEntero)) return;

        BigDecimal importe = parseAmount(importeRaw);
        BigDecimal factor = BigDecimal.ONE.add(tipoIva.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        BigDecimal base = importe.divide(factor, 2, RoundingMode.HALF_UP);
        BigDecimal cuota = importe.subtract(base);

        ConceptoUnificado unificado = resolveConceptoMoeve(concepto);
        conceptos.add(FacturaConceptoResumen.builder()
                .conceptoOriginal(concepto)
                .conceptoUnificado(unificado.name())
                .cantidad(cantidadRaw != null ? parseAmount(cantidadRaw) : null)
                .baseImponible(base)
                .tipoIva(tipoIva)
                .cuotaIva(cuota)
                .importe(importe)
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXTRACTO
    // ─────────────────────────────────────────────────────────────────────────

    void parseExtracto(List<String> lines, List<TarjetaResumen> tarjetaResumenes) {
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

            // Líneas de cierre de bloque de tarjeta que NO son operación ni un
            // nuevo bloque, aunque contengan literalmente "MATRICULA:"/"PAN:"
            // (p. ej. "TOTAL MATRICULA: X /PAN: Y,AGRUPADAS..."). Deben
            // comprobarse ANTES que los detectores de bloque de tarjeta para
            // no crear una tarjeta fantasma con cada resumen de cierre.
            // El "." tolera tanto la tilde real ("MATRÍCULA") como el "?"
            // literal que PDFBox extrae de algunas facturas PT cuya fuente no
            // mapea bien los caracteres acentuados a Unicode.
            if (line.matches("(?s)TOTAL MATR.CULA.*")
                    || line.contains("REGISTRATE EN WWW")
                    || line.contains("GESTIONA ON LINE")
                    || line.contains("PAG.:")) {
                continue;
            }

            // Bloque de nueva tarjeta (Family A: "*** MATRICULA/TARJETA X *** Y ***").
            // NEW-11: en MOEVE el conductor NO viene en el extracto (todas las
            // líneas no numéricas son nombres de estación de servicio). El
            // conductor se infiere del maestro de tarjetas.
            Matcher tarjetaM = P_TARJETA_BLOCK.matcher(stripAccents(line).toUpperCase(Locale.ROOT));
            if (tarjetaM.find()) {
                String numTarjeta    = tarjetaM.group(2);
                // Sin identificador propio (p. ej. peajes/gestión de red): se usa
                // el propio número de tarjeta como alias.
                String identificador = tarjetaM.group(1) != null ? tarjetaM.group(1) : numTarjeta;

                currentTarjeta = nuevaTarjetaResumen(identificador, numTarjeta, tarjetaResumenes);
                currentFechaOp = null;
                currentHora = null;
                currentEstablecimiento = null;
                continue;
            }

            // Bloque de nueva tarjeta (Family B: "MATRICULA: X / PAN: Y", sin
            // asteriscos; también cubre "MATRÍCULA:" vía plegado de acentos).
            Matcher tarjetaBM = P_TARJETA_BLOCK_B.matcher(stripAccents(line).toUpperCase(Locale.ROOT));
            if (tarjetaBM.find()) {
                String identificador = tarjetaBM.group(1);
                String numTarjeta    = tarjetaBM.group(2);

                currentTarjeta = nuevaTarjetaResumen(identificador, numTarjeta, tarjetaResumenes);
                currentFechaOp = null;
                currentHora = null;
                currentEstablecimiento = null;
                continue;
            }

            if (currentTarjeta == null) continue;

            // Detectar línea de operación Family A (empieza con número largo
            // seguido de concepto)
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

            // Detectar línea de operación Family B (establecimiento + fecha
            // dd-MM-yyyy + hora HH:mm[:ss] + concepto, todo en la misma línea)
            Matcher opBM = P_OP_LINE_B.matcher(line);
            if (opBM.matches()) {
                try {
                    Operacion op = parseLineaOperacionMoeveFamilyB(opBM);
                    op.setTarjetaResumen(currentTarjeta);
                    currentTarjeta.getOperaciones().add(op);
                } catch (Exception e) {
                    log.warn("[Moeve] Error parseando operación Family B '{}': {}", line, e.getMessage());
                }
                continue;
            }

            // Detectar línea de operación Family B sin establecimiento propio
            // (la fecha abre la línea directamente; el establecimiento ya se
            // acumuló en currentEstablecimiento desde líneas de texto previas).
            Matcher opBSinEstM = P_OP_LINE_B_SIN_ESTABLECIMIENTO.matcher(line);
            if (opBSinEstM.matches() && currentEstablecimiento != null) {
                try {
                    Operacion op = parseLineaOperacionMoeveFamilyBSinEstablecimiento(
                            opBSinEstM, currentEstablecimiento);
                    op.setTarjetaResumen(currentTarjeta);
                    currentTarjeta.getOperaciones().add(op);
                } catch (Exception e) {
                    log.warn("[Moeve] Error parseando operación Family B sin establecimiento '{}': {}",
                            line, e.getMessage());
                }
                // El establecimiento ya se ha consumido; resetear para no
                // arrastrarlo a la siguiente operación.
                currentEstablecimiento = null;
                continue;
            }

            // Detectar línea de operación de peaje/red Family A (establecimiento
            // genérico + fecha yyyy-MM-dd + hora HH:mm + número de operación +
            // concepto sin palabra clave de combustible, p. ej. "USO RED PORTUGAL")
            Matcher opPeajeM = P_OP_LINE_PEAJE.matcher(line);
            if (opPeajeM.matches()) {
                try {
                    Operacion op = parseLineaOperacionPeaje(opPeajeM);
                    op.setTarjetaResumen(currentTarjeta);
                    currentTarjeta.getOperaciones().add(op);
                } catch (Exception e) {
                    log.warn("[Moeve] Error parseando operación de peaje '{}': {}", line, e.getMessage());
                }
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

    private static TarjetaResumen nuevaTarjetaResumen(String identificador, String numTarjeta,
            List<TarjetaResumen> tarjetaResumenes) {
        TarjetaResumen tarjeta = TarjetaResumen.builder()
                .numTarjeta(numTarjeta)
                .alias(identificador)
                .conceptos(new ArrayList<>())
                .operaciones(new ArrayList<>())
                .build();
        tarjetaResumenes.add(tarjeta);
        return tarjeta;
    }

    /** Resultado de separar el concepto de los tokens numéricos que le siguen en una línea de operación. */
    private record ConceptoSplit(String concepto, int nextIndex) {
    }

    /**
     * Detecta si un token de línea de operación es numérico (importe/cantidad),
     * ignorando un posible prefijo "*" (precio destacado) o signo "-" (importes
     * negativos, p. ej. descuentos).
     */
    private static boolean isNumericToken(String tok) {
        String s = tok.startsWith("*") ? tok.substring(1) : tok;
        if (s.startsWith("-")) s = s.substring(1);
        return s.matches("[\\d]+,[\\d]+") || s.matches("[\\d]+\\.[\\d]+,[\\d]+");
    }

    /**
     * Recoge, desde {@code fromIndex}, los tokens no numéricos consecutivos
     * como concepto (nombre de producto/servicio), deteniéndose en el primer
     * token numérico (típicamente los litros/cantidad).
     */
    private static ConceptoSplit splitConceptoNumerico(String[] tokens, int fromIndex) {
        StringBuilder sb = new StringBuilder();
        int i = fromIndex;
        while (i < tokens.length && !isNumericToken(tokens[i])) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(tokens[i]);
            i++;
        }
        return new ConceptoSplit(sb.toString().strip(), i);
    }

    /**
     * Recoge, a partir de {@code fromIndex}, todos los tokens numéricos de la
     * línea (ignorando literales de texto intercalados, p. ej. "Lt.", códigos
     * de descuento como "DC-EURO/LT", "LIS", "DESC"), conservando el signo
     * negativo cuando lo haya.
     */
    private static List<String> collectNumericTokens(String[] tokens, int fromIndex) {
        List<String> numTokens = new ArrayList<>();
        for (int i = fromIndex; i < tokens.length; i++) {
            String tok = tokens[i];
            if (isNumericToken(tok)) {
                numTokens.add(tok.startsWith("*") ? tok.substring(1) : tok);
            }
        }
        return numTokens;
    }

    /**
     * Construye la {@link Operacion} final a partir de los tokens numéricos ya
     * recogidos. Los primeros campos (litros, precioUnitario, importeConIVA)
     * son posicionales desde el principio porque su orden es estable; en
     * cambio, el descuento y, sobre todo, el importe realmente cobrado
     * (importeTotal) se leen desde el FINAL de la lista, sea cual sea el
     * número de campos intermedios presentes — mismo criterio validado en
     * RepsolFacturaParser (los peajes solo traen 2 números, el combustible
     * puede traer hasta 6; indexar desde el final es el único criterio que
     * se cumple en ambos casos).
     */
    private Operacion buildOperacionFromNumericTail(String referencia, String conceptoOriginal,
            LocalDateTime fechaHora, String establecimiento, String conductor, List<String> numTokens) {
        ConceptoUnificado conceptoUnificado = resolveConceptoMoeve(conceptoOriginal);

        BigDecimal litros         = numTokens.size() > 0 ? parseAmount(numTokens.get(0)) : null;
        BigDecimal precioUnitario = numTokens.size() > 1 ? parseAmount(numTokens.get(1)) : null;
        BigDecimal importeConIVA  = numTokens.size() > 2 ? parseAmount(numTokens.get(2)) : null;
        BigDecimal dtoTotal       = numTokens.size() > 3
                ? parseAmount(numTokens.get(numTokens.size() - 2)) : null;
        BigDecimal importeTotal   = numTokens.isEmpty()
                ? null : parseAmount(numTokens.get(numTokens.size() - 1));

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
                .importeTotal(importeTotal)
                .build();
    }

    /**
     * Parsea una línea de operación MOEVE Family A del extracto (referencia
     * numérica al principio; fecha/hora se acumulan por separado en líneas
     * previas).
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
        ConceptoSplit split = splitConceptoNumerico(tokens, 1);
        List<String> numTokens = collectNumericTokens(tokens, split.nextIndex());

        LocalDateTime fechaHora = null;
        if (fecha != null) {
            int hh = 0, mm = 0;
            if (hora != null && hora.contains(":")) {
                hh = Integer.parseInt(hora.substring(0, 2));
                mm = Integer.parseInt(hora.substring(3, 5));
            }
            fechaHora = fecha.atTime(hh, mm);
        }

        return buildOperacionFromNumericTail(referencia, split.concepto(), fechaHora, establecimiento,
                conductor, numTokens);
    }

    /**
     * Parsea una línea de operación MOEVE Family B del extracto (establecimiento,
     * fecha dd-MM-yyyy y hora en la propia línea; sin referencia numérica).
     * Los segundos son opcionales (las facturas PT reales solo traen HH:mm).
     *
     * Formato: {establecimiento} {dd-MM-yyyy} {HH:mm[:ss]} {concepto} {litros} Lt. {precioUnitario} {importeConIVA} [{tipoDesc}] {descuento} {importeFinal}
     * Ejemplo: "E.S. HENARES 10-03-2026 19:59:30 DIESEL STAR 46,95 Lt. 1,819 85,40 LIS -0,704 84,70"
     */
    private Operacion parseLineaOperacionMoeveFamilyB(Matcher m) {
        String establecimiento = m.group(1).strip();
        LocalDate fecha = LocalDate.parse(m.group(2), FMT_DD_MM_YYYY);
        LocalDateTime fechaHora = fecha.atTime(parseHoraOpcionalSegundos(m.group(3)));

        String[] tokens = m.group(4).strip().split("\\s+");
        ConceptoSplit split = splitConceptoNumerico(tokens, 0);
        List<String> numTokens = collectNumericTokens(tokens, split.nextIndex());

        return buildOperacionFromNumericTail(null, split.concepto(), fechaHora, establecimiento, null,
                numTokens);
    }

    /**
     * Igual que {@link #parseLineaOperacionMoeveFamilyB} pero para cuando la
     * línea de operación NO trae establecimiento (empieza directamente por la
     * fecha), porque el nombre de la estación se partió en líneas de texto
     * anteriores y ya viene acumulado en {@code establecimiento}.
     */
    private Operacion parseLineaOperacionMoeveFamilyBSinEstablecimiento(Matcher m, String establecimiento) {
        LocalDate fecha = LocalDate.parse(m.group(1), FMT_DD_MM_YYYY);
        LocalDateTime fechaHora = fecha.atTime(parseHoraOpcionalSegundos(m.group(2)));

        String[] tokens = m.group(3).strip().split("\\s+");
        ConceptoSplit split = splitConceptoNumerico(tokens, 0);
        List<String> numTokens = collectNumericTokens(tokens, split.nextIndex());

        return buildOperacionFromNumericTail(null, split.concepto(), fechaHora,
                trimEstablecimiento(establecimiento), null, numTokens);
    }

    /**
     * Parsea una línea de operación MOEVE de peaje/red Family A del extracto
     * (establecimiento genérico, fecha yyyy-MM-dd y hora HH:mm en la propia
     * línea, seguidas del número de operación; sin palabra clave de
     * combustible, p. ej. peajes "USO RED PORTUGAL").
     *
     * Formato: {establecimiento} {yyyy-MM-dd} {HH:mm} {numOperacion} {concepto} {importe}
     * Ejemplo: "OPERACIONES GESTION SERVICIOS 2026-03-31 16:07 06401064194 USO RED PORTUGAL 1,21"
     */
    private Operacion parseLineaOperacionPeaje(Matcher m) {
        String establecimiento = m.group(1).strip();
        String fechaRaw = m.group(2);
        LocalDate fecha = fechaRaw.contains(".")
                ? LocalDate.parse(fechaRaw, FMT_DD_MM_YYYY_DOT)
                : LocalDate.parse(fechaRaw, FMT_YYYY_MM_DD);
        LocalDateTime fechaHora = fecha.atTime(parseHoraOpcionalSegundos(m.group(3)));
        String referencia = m.group(4);

        String[] tokens = m.group(5).strip().split("\\s+");
        ConceptoSplit split = splitConceptoNumerico(tokens, 0);
        List<String> numTokens = collectNumericTokens(tokens, split.nextIndex());

        return buildOperacionFromNumericTail(referencia, split.concepto(), fechaHora, establecimiento, null,
                numTokens);
    }

    /** Parsea "HH:mm" o "HH:mm:ss" (segundos opcionales) a {@link java.time.LocalTime}. */
    private static java.time.LocalTime parseHoraOpcionalSegundos(String horaStr) {
        int hh = Integer.parseInt(horaStr.substring(0, 2));
        int mm = Integer.parseInt(horaStr.substring(3, 5));
        int ss = horaStr.length() >= 8 ? Integer.parseInt(horaStr.substring(6, 8)) : 0;
        return java.time.LocalTime.of(hh, mm, ss);
    }

    /**
     * Parsea "dd-MM-yy" (año de 2 dígitos, formato ES) o "dd-MM-yyyy" (año de
     * 4 dígitos, formato PT observado) a {@link LocalDate}. Devuelve
     * {@code null} si no coincide con ninguno de los dos formatos.
     */
    private static LocalDate parseFechaFlexible(String value) {
        try {
            return LocalDate.parse(value, FMT_DD_MM_YY);
        } catch (Exception ignored) {
            try {
                return LocalDate.parse(value, FMT_DD_MM_YYYY);
            } catch (Exception ignored2) {
                return null;
            }
        }
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

    BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank() || s.equals("-")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.replace(".", "").replace(",", "."));
        } catch (NumberFormatException e) {
            log.debug("[Moeve] No se pudo parsear importe '{}'", s);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Elimina diacríticos (acentos) de una cadena, p. ej. "NÚMERO" -> "NUMERO".
     * Permite comparar palabras clave de forma insensible a tildes.
     */
    static String stripAccents(String s) {
        if (s == null) return null;
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    private static boolean isNumeroKeywordLine(String line) {
        String folded = stripAccents(line.strip()).toUpperCase(Locale.ROOT);
        return NUMERO_KEYWORDS_FOLDED.contains(folded);
    }

    /**
     * Extrae el número de factura buscando, en orden de aparición, líneas que
     * sean una palabra clave equivalente a "NUMERO" (ES: NUMERO/NÚMERO, PT:
     * FATURA) seguidas del valor en la línea siguiente, o el formato inline de
     * Family A ("1- {numero}"). Si aparece más de una coincidencia, prevalece
     * la última encontrada (igual que el comportamiento previo del parser).
     *
     * ADVERTENCIA: en la muestra PT observada, "FATURA" aparece como título del
     * documento (banner), no como etiqueta del campo número — la etiqueta real
     * en esa factura sigue siendo "NÚMERO". El resultado final es correcto
     * solo porque "FATURA" aparece ANTES que "NÚMERO" en el documento y gana
     * la última coincidencia; no es un invariante garantizado por diseño. Si
     * una futura plantilla PT invierte ese orden, o el número real aparece
     * justo después del banner "FATURA" sin una línea "NÚMERO" posterior, esta
     * extracción se rompería silenciosamente. Revisar con una factura PT real
     * adicional si aparecen números de factura incorrectos en producción.
     */
    String extractNumFactura(List<String> lines) {
        String numFactura = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (isNumeroKeywordLine(line) && i + 1 < lines.size()) {
                String next = lines.get(i + 1).strip();
                if (!next.isBlank()) {
                    numFactura = next;
                }
            }

            Matcher inlineM = P_NUMERO_INLINE.matcher(line);
            if (inlineM.find()) {
                numFactura = inlineM.group(1);
            }
        }
        return numFactura;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DETECCIÓN DE PLANTILLA (Family A / Family B, ES / PT)
    // ─────────────────────────────────────────────────────────────────────────

    /** Familia de plantilla Moeve/Cepsa: nested/B2G (A) o flat/Cobros B2B (B). */
    enum TemplateFamily {
        FAMILY_A,
        FAMILY_B
    }

    /** Resultado de {@link #detectTemplate(List)}: familia + idioma detectados. */
    record TemplateDetection(TemplateFamily family, InvoiceLocale locale) {
    }

    /**
     * Detecta la familia de plantilla y el idioma de la factura mediante
     * señales ponderadas en la cabecera. Ante empate o ausencia total de
     * señales, se asume FAMILY_A (la plantilla parcialmente funcional hoy)
     * para no romper el caso ya soportado, dejando constancia en el log.
     */
    TemplateDetection detectTemplate(List<String> lines) {
        boolean sawInlineNumero = false;
        boolean sawImporteSinIva = false;
        boolean sawMatriculaAsterisco = false;
        boolean sawCobrosOrDebito = false;
        boolean sawMonedaHeader = false;
        boolean sawMatriculaPan = false;
        boolean localePt = false;

        for (String rawLine : lines) {
            String line = rawLine.strip();
            String upper = stripAccents(line).toUpperCase(Locale.ROOT);

            if (P_NUMERO_INLINE.matcher(line).find()) {
                sawInlineNumero = true;
            }
            if (upper.contains("IMPORTE SIN IVA")) {
                sawImporteSinIva = true;
            }
            if (upper.contains("MATRICULA/TARJETA")) {
                sawMatriculaAsterisco = true;
            }
            if (upper.contains("COBROS B2B") || upper.contains("DEBITO DIRECTO")) {
                sawCobrosOrDebito = true;
            }
            if (upper.contains("MONEDA") || upper.contains("MOEDA")) {
                sawMonedaHeader = true;
            }
            if (upper.contains("MATRICULA:") && upper.contains("PAN:")) {
                sawMatriculaPan = true;
            }
            // Señales de idioma PT. Cada una tolera una variante real distinta:
            // "FATURA" a veces se imprime con espacio entre cada letra
            // ("F A T U R A"); "RESUMO IVA" a veces trae un símbolo "%1%" en
            // vez de la palabra "IVA" ("RESUMO %1% BASE..."); "DESCRIÇÃO" a
            // veces sale con "?" literal en vez de las tildes (fuente PT mal
            // mapeada, mismo problema ya visto en el marcador de tarjeta).
            if (upper.equals("FATURA") || upper.replace(" ", "").equals("FATURA")
                    || upper.contains("RESUMO")
                    || upper.matches(".*DESCRI..O.*")) {
                localePt = true;
            }
        }

        int scoreA = (sawInlineNumero ? 1 : 0) + (sawImporteSinIva ? 1 : 0) + (sawMatriculaAsterisco ? 1 : 0);
        int scoreB = (sawCobrosOrDebito ? 1 : 0) + (sawMonedaHeader ? 1 : 0) + (sawMatriculaPan ? 1 : 0);

        TemplateFamily family;
        if (scoreA > scoreB) {
            family = TemplateFamily.FAMILY_A;
        } else if (scoreB > scoreA) {
            family = TemplateFamily.FAMILY_B;
        } else {
            log.warn("[Moeve] detectTemplate: señales ambiguas o ausentes (scoreA={}, scoreB={}), "
                    + "se asume FAMILY_A por defecto", scoreA, scoreB);
            family = TemplateFamily.FAMILY_A;
        }

        InvoiceLocale locale = localePt ? InvoiceLocale.PT : InvoiceLocale.ES;
        return new TemplateDetection(family, locale);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOCALIZACIÓN (ES / PT)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Localización de la factura: tipos de IVA válidos y léxico de palabras
     * clave equivalentes por idioma. Cerrado a los dos únicos idiomas
     * soportados por Moeve/Cepsa (España y Portugal); no es un framework
     * multi-país genérico.
     */
    enum InvoiceLocale {
        // 0% es un tipo real observado (combustible con impuestos especiales
        // ya incluidos en el precio, p. ej. "II.EE. INCLUIDOS EN EL PRECIO").
        ES(new int[] {21, 10, 4, 0}, Map.of(
                "NUMERO", "NUMERO",
                "RESUMEN", "RESUMEN IVA",
                "DESCUENTO", "DESCUENTO")),
        PT(new int[] {23}, Map.of(
                "NUMERO", "FATURA",
                "RESUMEN", "RESUMO IVA",
                "DESCUENTO", "DESCONTO"));

        private final int[] tiposIva;
        private final Map<String, String> lexicon;

        InvoiceLocale(int[] tiposIva, Map<String, String> lexicon) {
            this.tiposIva = tiposIva;
            this.lexicon = lexicon;
        }

        boolean isValidTipoIva(int tipo) {
            return Arrays.stream(tiposIva).anyMatch(t -> t == tipo);
        }

        int[] tiposIva() {
            return tiposIva;
        }

        String lexicon(String key) {
            return lexicon.get(key);
        }
    }
}
