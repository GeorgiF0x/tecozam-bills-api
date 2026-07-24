package com.tecozam.bills.factura.infrastructure.parser;

import com.tecozam.bills.factura.domain.Factura;
import com.tecozam.bills.factura.domain.FacturaConceptoResumen;
import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.domain.TarjetaResumen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MoeveFacturaParserTest {

    private final MoeveFacturaParser parser = new MoeveFacturaParser();

    private static List<String> loadFixture(String filename) throws IOException {
        try (InputStream is = MoeveFacturaParserTest.class.getResourceAsStream("/fixtures/moeve/" + filename)) {
            if (is == null) {
                throw new IllegalArgumentException("Fixture no encontrada: " + filename);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::strip)
                        .filter(l -> !l.isBlank())
                        .toList();
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // parseAmount
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("parseAmount conserva el signo negativo en importes tipo DESCUENTO")
    void parseAmountConservaSignoNegativo() {
        BigDecimal resultado = parser.parseAmount("-18,04");
        assertThat(resultado).isEqualByComparingTo(new BigDecimal("-18.04"));
    }

    @Test
    @DisplayName("parseAmount trata un guion aislado como importe ausente (cero)")
    void parseAmountGuionAisladoEsCero() {
        assertThat(parser.parseAmount("-")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("parseAmount conserva el signo negativo con miles y decimales")
    void parseAmountConservaSignoNegativoConMiles() {
        BigDecimal resultado = parser.parseAmount("-1.234,56");
        assertThat(resultado).isEqualByComparingTo(new BigDecimal("-1234.56"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // InvoiceLocale
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("InvoiceLocale ES admite tipos de IVA 21, 10 y 4, y usa léxico español")
    void invoiceLocaleEsRatesYLexico() {
        assertThat(MoeveFacturaParser.InvoiceLocale.ES.isValidTipoIva(21)).isTrue();
        assertThat(MoeveFacturaParser.InvoiceLocale.ES.isValidTipoIva(10)).isTrue();
        assertThat(MoeveFacturaParser.InvoiceLocale.ES.isValidTipoIva(4)).isTrue();
        assertThat(MoeveFacturaParser.InvoiceLocale.ES.isValidTipoIva(23)).isFalse();
        assertThat(MoeveFacturaParser.InvoiceLocale.ES.lexicon("RESUMEN")).isEqualTo("RESUMEN IVA");
        assertThat(MoeveFacturaParser.InvoiceLocale.ES.lexicon("DESCUENTO")).isEqualTo("DESCUENTO");
        assertThat(MoeveFacturaParser.InvoiceLocale.ES.lexicon("NUMERO")).isEqualTo("NUMERO");
    }

    @Test
    @DisplayName("InvoiceLocale PT admite únicamente el tipo de IVA 23, y usa léxico portugués")
    void invoiceLocalePtRatesYLexico() {
        assertThat(MoeveFacturaParser.InvoiceLocale.PT.isValidTipoIva(23)).isTrue();
        assertThat(MoeveFacturaParser.InvoiceLocale.PT.isValidTipoIva(21)).isFalse();
        assertThat(MoeveFacturaParser.InvoiceLocale.PT.lexicon("RESUMEN")).isEqualTo("RESUMO IVA");
        assertThat(MoeveFacturaParser.InvoiceLocale.PT.lexicon("DESCUENTO")).isEqualTo("DESCONTO");
        assertThat(MoeveFacturaParser.InvoiceLocale.PT.lexicon("NUMERO")).isEqualTo("FATURA");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // detectTemplate
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("detectTemplate reconoce Family A (ES) en una factura con formato inline y marca de tarjeta clásica")
    void detectTemplateFamilyA() throws IOException {
        List<String> lines = loadFixture("familyA_basic.txt");
        MoeveFacturaParser.TemplateDetection detection = parser.detectTemplate(lines);
        assertThat(detection.family()).isEqualTo(MoeveFacturaParser.TemplateFamily.FAMILY_A);
        assertThat(detection.locale()).isEqualTo(MoeveFacturaParser.InvoiceLocale.ES);
    }

    @Test
    @DisplayName("detectTemplate reconoce Family B (ES) en una factura Cobros B2B con columna MONEDA")
    void detectTemplateFamilyB() throws IOException {
        List<String> lines = loadFixture("familyB_basic.txt");
        MoeveFacturaParser.TemplateDetection detection = parser.detectTemplate(lines);
        assertThat(detection.family()).isEqualTo(MoeveFacturaParser.TemplateFamily.FAMILY_B);
        assertThat(detection.locale()).isEqualTo(MoeveFacturaParser.InvoiceLocale.ES);
    }

    @Test
    @DisplayName("detectTemplate reconoce Family B con locale PT en una factura con FATURA/RESUMO IVA/DESCRICAO")
    void detectTemplateFamilyBPt() throws IOException {
        List<String> lines = loadFixture("familyB_pt.txt");
        MoeveFacturaParser.TemplateDetection detection = parser.detectTemplate(lines);
        assertThat(detection.family()).isEqualTo(MoeveFacturaParser.TemplateFamily.FAMILY_B);
        assertThat(detection.locale()).isEqualTo(MoeveFacturaParser.InvoiceLocale.PT);
    }

    @Test
    @DisplayName("detectTemplate reconoce locale PT aunque \"FATURA\" venga espaciada letra por letra, \"RESUMO IVA\" traiga \"%1%\" en vez de IVA, y \"DESCRIÇÃO\" salga con '?' literal")
    void detectTemplateLocalePtConVariantesRealesMangled() {
        List<String> lines = List.of(
                "1- AF8350000000061705",
                "F A T U R A",
                "DESCRI??O QUANTIDADE UNIT?RIO SEM %1% % VALOR COM %1%",
                "DESCONTO NA FATURA -268,5366 23 -61,7634 -330,3000",
                "TOTAL DOCUMENTO EUROS 3.238,3252 744,8148 3.983,1400",
                "RESUMO %1% BASE DE TAXA VALOR",
                "3.238,33 23 744,81 3.983,14");
        MoeveFacturaParser.TemplateDetection detection = parser.detectTemplate(lines);
        assertThat(detection.locale()).isEqualTo(MoeveFacturaParser.InvoiceLocale.PT);
    }

    @Test
    @DisplayName("detectTemplate ante señales ambiguas o ausentes recae por defecto en Family A")
    void detectTemplateFallbackAmbiguo() {
        List<String> lines = List.of("ENCABEZADO IRRELEVANTE", "SIN SEÑALES DE FAMILIA CONOCIDAS");
        MoeveFacturaParser.TemplateDetection detection = parser.detectTemplate(lines);
        assertThat(detection.family()).isEqualTo(MoeveFacturaParser.TemplateFamily.FAMILY_A);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // extractNumFactura
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("extractNumFactura reconoce el formato inline '1- {numero}' de Family A")
    void extractNumFacturaFamilyAInline() throws IOException {
        List<String> lines = loadFixture("familyA_basic.txt");
        assertThat(parser.extractNumFactura(lines)).isEqualTo("987654");
    }

    @Test
    @DisplayName("extractNumFactura reconoce la palabra clave NUMERO (sin tilde) de Family B")
    void extractNumFacturaFamilyBKeyword() throws IOException {
        List<String> lines = loadFixture("familyB_basic.txt");
        assertThat(parser.extractNumFactura(lines)).isEqualTo("BA7240000123456");
    }

    @Test
    @DisplayName("extractNumFactura reconoce la palabra clave FATURA en facturas PT")
    void extractNumFacturaFaturaPt() throws IOException {
        List<String> lines = loadFixture("familyB_pt.txt");
        assertThat(parser.extractNumFactura(lines)).isEqualTo("FT A/00000023334");
    }

    @Test
    @DisplayName("extractNumFactura reconoce NÚMERO con tilde de forma insensible a acentos")
    void extractNumFacturaAccentInsensitiveConTilde() throws IOException {
        List<String> lines = loadFixture("edge_accent_numero.txt");
        assertThat(parser.extractNumFactura(lines.subList(0, 2))).isEqualTo("BA7240000998877");
    }

    @Test
    @DisplayName("extractNumFactura reconoce NUMERO sin tilde")
    void extractNumFacturaAccentInsensitiveSinTilde() throws IOException {
        List<String> lines = loadFixture("edge_accent_numero.txt");
        assertThat(parser.extractNumFactura(lines.subList(2, 4))).isEqualTo("BA7240000112233");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // parseResumenIva
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("parseResumenIva extrae base, cuota y total desde RESUMEN IVA en Family A (sin código de moneda)")
    void parseResumenIvaFamilyA() throws IOException {
        List<String> lines = loadFixture("familyA_basic.txt");
        MoeveFacturaParser.ResumenIvaTotales totales =
                parser.parseResumenIva(lines, MoeveFacturaParser.InvoiceLocale.ES);
        assertThat(totales).isNotNull();
        assertThat(totales.baseImponible()).isEqualByComparingTo(new BigDecimal("450.00"));
        assertThat(totales.totalIva()).isEqualByComparingTo(new BigDecimal("94.50"));
        assertThat(totales.totalFactura()).isEqualByComparingTo(new BigDecimal("544.50"));
    }

    @Test
    @DisplayName("parseResumenIva extrae totales desde RESUMEN IVA en Family B con tipo distinto de cero")
    void parseResumenIvaFamilyBTipoDistintoDeCero() throws IOException {
        List<String> lines = loadFixture("familyB_basic.txt");
        MoeveFacturaParser.ResumenIvaTotales totales =
                parser.parseResumenIva(lines, MoeveFacturaParser.InvoiceLocale.ES);
        assertThat(totales).isNotNull();
        assertThat(totales.baseImponible()).isEqualByComparingTo(new BigDecimal("6158.10"));
        assertThat(totales.totalIva()).isEqualByComparingTo(new BigDecimal("1293.20"));
        assertThat(totales.totalFactura()).isEqualByComparingTo(new BigDecimal("7451.30"));
    }

    @Test
    @DisplayName("parseResumenIva extrae totales negativos en una nota de abono (factura en negativo)")
    void parseResumenIvaNotaDeAbonoImportesNegativos() throws IOException {
        List<String> lines = loadFixture("familyB_nota_abono.txt");
        MoeveFacturaParser.ResumenIvaTotales totales =
                parser.parseResumenIva(lines, MoeveFacturaParser.InvoiceLocale.ES);
        assertThat(totales).isNotNull();
        assertThat(totales.baseImponible()).isEqualByComparingTo(new BigDecimal("-166.79"));
        assertThat(totales.totalIva()).isEqualByComparingTo(new BigDecimal("-35.02"));
        assertThat(totales.totalFactura()).isEqualByComparingTo(new BigDecimal("-201.81"));
    }

    @Test
    @DisplayName("parseResumenIva deriva la cuota (total - base) cuando la fila solo trae base, tipo y total")
    void parseResumenIvaFamilyBFilaDeTresNumeros() throws IOException {
        List<String> lines = loadFixture("familyB_resumen_simple.txt");
        MoeveFacturaParser.ResumenIvaTotales totales =
                parser.parseResumenIva(lines, MoeveFacturaParser.InvoiceLocale.ES);
        assertThat(totales).isNotNull();
        assertThat(totales.baseImponible()).isEqualByComparingTo(new BigDecimal("103.01"));
        assertThat(totales.totalIva()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(totales.totalFactura()).isEqualByComparingTo(new BigDecimal("103.01"));
    }

    @Test
    @DisplayName("parseResumenIva aplica el 23% real en facturas PT, nunca el 21% español")
    void parseResumenIvaPt23Porciento() throws IOException {
        List<String> lines = loadFixture("familyB_pt.txt");
        MoeveFacturaParser.ResumenIvaTotales totales =
                parser.parseResumenIva(lines, MoeveFacturaParser.InvoiceLocale.PT);
        assertThat(totales).isNotNull();
        assertThat(totales.baseImponible()).isEqualByComparingTo(new BigDecimal("727.48"));
        assertThat(totales.totalIva()).isEqualByComparingTo(new BigDecimal("167.32"));
        assertThat(totales.totalFactura()).isEqualByComparingTo(new BigDecimal("894.80"));
        assertThat(MoeveFacturaParser.InvoiceLocale.PT.isValidTipoIva(23)).isTrue();
        assertThat(MoeveFacturaParser.InvoiceLocale.PT.isValidTipoIva(21)).isFalse();
    }

    @Test
    @DisplayName("parseResumenIva devuelve null cuando no hay bloque RESUMEN/RESUMO IVA")
    void parseResumenIvaSinBloqueDevuelveNull() {
        List<String> lines = List.of("SIN BLOQUE DE RESUMEN", "TOTAL DOCUMENTO EUR 100,00");
        MoeveFacturaParser.ResumenIvaTotales totales =
                parser.parseResumenIva(lines, MoeveFacturaParser.InvoiceLocale.ES);
        assertThat(totales).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // parseCabecera — wiring de totales vía RESUMEN/RESUMO IVA
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("parseCabecera usa RESUMEN IVA como fuente de totales en Family A")
    void parseCabeceraFamilyAUsaResumenIva() throws IOException {
        List<String> lines = loadFixture("familyA_basic.txt");
        Factura.FacturaBuilder builder = Factura.builder();
        parser.parseCabecera(lines, builder, MoeveFacturaParser.InvoiceLocale.ES);
        Factura factura = builder.build();
        assertThat(factura.getBaseImponible()).isEqualByComparingTo(new BigDecimal("450.00"));
        assertThat(factura.getTotalIva()).isEqualByComparingTo(new BigDecimal("94.50"));
        assertThat(factura.getTotalFactura()).isEqualByComparingTo(new BigDecimal("544.50"));
    }

    @Test
    @DisplayName("parseCabecera usa RESUMO IVA como fuente de totales en facturas PT (23%, no 21%)")
    void parseCabeceraPtUsaResumoIva() throws IOException {
        List<String> lines = loadFixture("familyB_pt.txt");
        Factura.FacturaBuilder builder = Factura.builder();
        parser.parseCabecera(lines, builder, MoeveFacturaParser.InvoiceLocale.PT);
        Factura factura = builder.build();
        assertThat(factura.getBaseImponible()).isEqualByComparingTo(new BigDecimal("727.48"));
        assertThat(factura.getTotalIva()).isEqualByComparingTo(new BigDecimal("167.32"));
        assertThat(factura.getTotalFactura()).isEqualByComparingTo(new BigDecimal("894.80"));
    }

    @Test
    @DisplayName("parseCabecera extrae número de cuenta y NIF de la línea combinada \"{numero} {NIF}\"")
    void parseCabeceraExtraeNumCuentaYNif() throws IOException {
        List<String> lines = loadFixture("familyA_cuenta_nif.txt");
        Factura.FacturaBuilder builder = Factura.builder();
        parser.parseCabecera(lines, builder, MoeveFacturaParser.InvoiceLocale.ES);
        Factura factura = builder.build();
        assertThat(factura.getNumCuenta()).isEqualTo("9033380018142");
        assertThat(factura.getNifCliente()).isEqualTo("ESB83214668");
    }

    @Test
    @DisplayName("parseCabecera extrae la fecha de factura en portugués (\"DATA\", no \"FECHA\")")
    void parseCabeceraExtraeFechaEnPortugues() throws IOException {
        List<String> lines = loadFixture("familyB_pt_fecha_data.txt");
        Factura.FacturaBuilder builder = Factura.builder();
        parser.parseCabecera(lines, builder, MoeveFacturaParser.InvoiceLocale.PT);
        Factura factura = builder.build();
        assertThat(factura.getFecha()).isEqualTo(java.time.LocalDate.of(2026, 6, 30));
    }

    @Test
    @DisplayName("parseCabecera extrae el vencimiento en portugués (\"DATA DE VENC.\", año de 4 dígitos)")
    void parseCabeceraExtraeVencimientoEnPortugues() throws IOException {
        List<String> lines = loadFixture("familyB_pt_vencimiento_data.txt");
        Factura.FacturaBuilder builder = Factura.builder();
        parser.parseCabecera(lines, builder, MoeveFacturaParser.InvoiceLocale.PT);
        Factura factura = builder.build();
        assertThat(factura.getVencimiento()).isEqualTo(java.time.LocalDate.of(2026, 7, 10));
    }

    @Test
    @DisplayName("parseCabecera extrae el vencimiento con la palabra completa \"DATA DE VENCIMENTO\" (no solo la abreviatura \"VENC.\"), año de 2 dígitos")
    void parseCabeceraExtraeVencimientoPortuguesPalabraCompleta() throws IOException {
        List<String> lines = loadFixture("familyA_pt_vencimento_completo.txt");
        Factura.FacturaBuilder builder = Factura.builder();
        parser.parseCabecera(lines, builder, MoeveFacturaParser.InvoiceLocale.PT);
        Factura factura = builder.build();
        assertThat(factura.getVencimiento()).isEqualTo(java.time.LocalDate.of(2026, 3, 10));
    }

    @Test
    @DisplayName("derivarPeriodoDesdeOperaciones calcula período desde/hasta como fecha mínima/máxima de las operaciones (la factura Moeve no imprime un rango explícito)")
    void derivarPeriodoDesdeOperacionesUsaMinYMaxDeLasFechas() {
        TarjetaResumen tarjeta1 = TarjetaResumen.builder()
                .operaciones(new ArrayList<>(List.of(
                        Operacion.builder().fechaHora(LocalDateTime.of(2026, 5, 10, 9, 0)).build(),
                        Operacion.builder().fechaHora(LocalDateTime.of(2026, 5, 31, 23, 0)).build())))
                .build();
        TarjetaResumen tarjeta2 = TarjetaResumen.builder()
                .operaciones(new ArrayList<>(List.of(
                        Operacion.builder().fechaHora(LocalDateTime.of(2026, 5, 1, 0, 0)).build())))
                .build();

        Factura.FacturaBuilder builder = Factura.builder();
        parser.derivarPeriodoDesdeOperaciones(List.of(tarjeta1, tarjeta2), builder);
        Factura factura = builder.build();

        assertThat(factura.getPeriodoDesde()).isEqualTo(java.time.LocalDate.of(2026, 5, 1));
        assertThat(factura.getPeriodoHasta()).isEqualTo(java.time.LocalDate.of(2026, 5, 31));
    }

    @Test
    @DisplayName("derivarPeriodoDesdeOperaciones no falla ni establece nada si no hay operaciones con fecha")
    void derivarPeriodoDesdeOperacionesSinOperacionesNoHaceNada() {
        Factura.FacturaBuilder builder = Factura.builder();
        parser.derivarPeriodoDesdeOperaciones(List.of(), builder);
        Factura factura = builder.build();

        assertThat(factura.getPeriodoDesde()).isNull();
        assertThat(factura.getPeriodoHasta()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // parseConceptosResumen
    // ─────────────────────────────────────────────────────────────────────────

    /** Deriva base/cuota tal como especifica el diseño: base = importe/(1+tipo/100), cuota = importe-base. */
    private static BigDecimal derivarBaseEsperada(String importe, String tipo) {
        BigDecimal importeBd = new BigDecimal(importe);
        BigDecimal tipoBd = new BigDecimal(tipo);
        BigDecimal factor = BigDecimal.ONE.add(tipoBd.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
        return importeBd.divide(factor, 2, RoundingMode.HALF_UP);
    }

    @Test
    @DisplayName("parseConceptosResumen extrae conceptos de la tabla Family A (5 columnas, tipo 21%)")
    void parseConceptosResumenFamilyABasic() throws IOException {
        List<String> lines = loadFixture("familyA_basic.txt");
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();

        parser.parseConceptosResumen(lines, conceptos, MoeveFacturaParser.InvoiceLocale.ES);

        assertThat(conceptos).hasSize(1);
        FacturaConceptoResumen c = conceptos.get(0);
        assertThat(c.getConceptoOriginal()).isEqualTo("DIESEL STAR");
        assertThat(c.getCantidad()).isNull();
        assertThat(c.getBaseImponible()).isEqualByComparingTo(new BigDecimal("450.00"));
        assertThat(c.getTipoIva()).isEqualByComparingTo(new BigDecimal("21.00"));
        assertThat(c.getCuotaIva()).isEqualByComparingTo(new BigDecimal("94.50"));
        assertThat(c.getImporte()).isEqualByComparingTo(new BigDecimal("544.50"));
    }

    @Test
    @DisplayName("parseConceptosResumen Family A generaliza el tipo de IVA a 4/10/21/23, no sólo 21 fijo")
    void parseConceptosResumenFamilyAGeneralizaTipoIva() {
        List<String> lines = List.of(
                "CONCEPTO CANTIDAD IMPORTE SIN IVA IVA CUOTA IMPORTE CON IVA",
                "PRODUCTO BASICO 100,00 10 10,00 110,00");
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();

        parser.parseConceptosResumen(lines, conceptos, MoeveFacturaParser.InvoiceLocale.ES);

        assertThat(conceptos).hasSize(1);
        FacturaConceptoResumen c = conceptos.get(0);
        assertThat(c.getTipoIva()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(c.getBaseImponible()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(c.getCuotaIva()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(c.getImporte()).isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test
    @DisplayName("parseConceptosResumen reconstruye el concepto cuando su nombre se parte en 2 líneas por ser largo (\"PEAJES DE AUTOPISTAS/\" + \"TUNELES\")")
    void parseConceptosResumenReconstruyeConceptoPartidoEnDosLineas() throws IOException {
        List<String> lines = loadFixture("familyA_concepto_partido_dos_lineas.txt");
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();

        parser.parseConceptosResumen(lines, conceptos, MoeveFacturaParser.InvoiceLocale.ES);

        assertThat(conceptos).hasSize(1);
        FacturaConceptoResumen c = conceptos.get(0);
        assertThat(c.getConceptoOriginal()).isEqualTo("PEAJES DE AUTOPISTAS/ TUNELES");
        assertThat(c.getBaseImponible()).isEqualByComparingTo(new BigDecimal("3766.7025"));
        assertThat(c.getTipoIva()).isEqualByComparingTo(new BigDecimal("21.00"));
        assertThat(c.getCuotaIva()).isEqualByComparingTo(new BigDecimal("791.0075"));
        assertThat(c.getImporte()).isEqualByComparingTo(new BigDecimal("4557.7100"));
    }

    @Test
    @DisplayName("parseConceptosResumen reconoce la fila Family B con columna de cantidad (litros) y tipo 0% (factura real de 1 solo producto)")
    void parseConceptosResumenFamilyBConCantidadYTipoCero() throws IOException {
        List<String> lines = loadFixture("familyB_con_cantidad_tipo_cero.txt");
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();

        parser.parseConceptosResumen(lines, conceptos, MoeveFacturaParser.InvoiceLocale.ES);

        assertThat(conceptos).hasSize(1);
        FacturaConceptoResumen c = conceptos.get(0);
        assertThat(c.getConceptoOriginal()).isEqualTo("DIESEL STAR");
        assertThat(c.getCantidad()).isEqualByComparingTo(new BigDecimal("61.61"));
        assertThat(c.getTipoIva()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(c.getImporte()).isEqualByComparingTo(new BigDecimal("103.01"));
    }

    @Test
    @DisplayName("parseConceptosResumen reconoce la cabecera \"DESCRIÇÃO\" aunque PDFBox extraiga '?' en vez de las tildes (misma fuente PT mal mapeada que en el marcador de tarjeta)")
    void parseConceptosResumenReconoceDescricaoConSignoInterrogacion() throws IOException {
        List<String> lines = loadFixture("familyA_pt_descricao_mangled.txt");
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();

        parser.parseConceptosResumen(lines, conceptos, MoeveFacturaParser.InvoiceLocale.PT);

        assertThat(conceptos).hasSize(1);
        FacturaConceptoResumen c = conceptos.get(0);
        assertThat(c.getConceptoOriginal()).isEqualTo("DESCONTO NA FATURA");
        assertThat(c.getImporte()).isEqualByComparingTo(new BigDecimal("-330.3000"));
    }

    @Test
    @DisplayName("parseConceptosResumen extrae conceptos de la tabla Family B (3 columnas), derivando base/cuota y excluyendo SUBTOTAL")
    void parseConceptosResumenFamilyBBasic() throws IOException {
        List<String> lines = loadFixture("familyB_basic.txt");
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();

        parser.parseConceptosResumen(lines, conceptos, MoeveFacturaParser.InvoiceLocale.ES);

        assertThat(conceptos).hasSize(2);
        assertThat(conceptos).noneMatch(c -> c.getConceptoOriginal().equalsIgnoreCase("SUBTOTAL"));

        FacturaConceptoResumen diesel = conceptos.get(0);
        assertThat(diesel.getConceptoOriginal()).isEqualTo("DIESEL STAR");
        assertThat(diesel.getTipoIva()).isEqualByComparingTo(new BigDecimal("21.00"));
        assertThat(diesel.getImporte()).isEqualByComparingTo(new BigDecimal("663.45"));
        assertThat(diesel.getBaseImponible()).isEqualByComparingTo(derivarBaseEsperada("663.45", "21"));
        assertThat(diesel.getCuotaIva())
                .isEqualByComparingTo(new BigDecimal("663.45").subtract(derivarBaseEsperada("663.45", "21")));

        FacturaConceptoResumen descuento = conceptos.get(1);
        assertThat(descuento.getConceptoOriginal()).isEqualTo("DESCUENTOS TELEPEAJES");
        assertThat(descuento.getImporte()).isEqualByComparingTo(new BigDecimal("-18.04"));
        assertThat(descuento.getBaseImponible()).isEqualByComparingTo(derivarBaseEsperada("-18.04", "21"));
        assertThat(descuento.getCuotaIva())
                .isEqualByComparingTo(new BigDecimal("-18.04").subtract(derivarBaseEsperada("-18.04", "21")));
        assertThat(descuento.getBaseImponible()).isLessThan(BigDecimal.ZERO);
        assertThat(descuento.getCuotaIva()).isLessThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("parseConceptosResumen Family B admite múltiples tipos de IVA distintos en la misma tabla (10% y 21%)")
    void parseConceptosResumenFamilyBMultiplesTipos() throws IOException {
        List<String> lines = loadFixture("familyB_multitipo.txt");
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();

        parser.parseConceptosResumen(lines, conceptos, MoeveFacturaParser.InvoiceLocale.ES);

        assertThat(conceptos).hasSize(3);
        assertThat(conceptos).noneMatch(c -> c.getConceptoOriginal().equalsIgnoreCase("SUBTOTAL"));

        FacturaConceptoResumen gasolina = conceptos.get(0);
        assertThat(gasolina.getConceptoOriginal()).isEqualTo("GASOLINA 95");
        assertThat(gasolina.getTipoIva()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(gasolina.getBaseImponible()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(gasolina.getCuotaIva()).isEqualByComparingTo(new BigDecimal("20.00"));

        FacturaConceptoResumen diesel = conceptos.get(1);
        assertThat(diesel.getTipoIva()).isEqualByComparingTo(new BigDecimal("21.00"));
        assertThat(diesel.getBaseImponible()).isEqualByComparingTo(derivarBaseEsperada("663.45", "21"));
    }

    @Test
    @DisplayName("parseConceptosResumen aplica el 23% real en conceptos PT, derivando base/cuota y preservando el signo negativo del DESCONTO")
    void parseConceptosResumenPt23Porciento() throws IOException {
        List<String> lines = loadFixture("familyB_pt.txt");
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();

        parser.parseConceptosResumen(lines, conceptos, MoeveFacturaParser.InvoiceLocale.PT);

        assertThat(conceptos).hasSize(3);
        assertThat(conceptos).allMatch(c -> c.getTipoIva().compareTo(new BigDecimal("23.00")) == 0);

        FacturaConceptoResumen desconto = conceptos.stream()
                .filter(c -> c.getConceptoOriginal().equals("DESCONTO NA FATURA"))
                .findFirst().orElseThrow();
        assertThat(desconto.getImporte()).isEqualByComparingTo(new BigDecimal("-12.50"));
        assertThat(desconto.getBaseImponible()).isLessThan(BigDecimal.ZERO);
        assertThat(desconto.getCuotaIva()).isLessThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("parseConceptosResumen conserva el signo negativo de DESCUENTO/DESCONTO en ambos bloques (ES 21% y PT 23%) y excluye SUBTOTAL")
    void parseConceptosResumenEdgeDescuentoNegativo() throws IOException {
        List<String> lines = loadFixture("edge_descuento_negativo.txt");

        // Bloque ES: cabecera + 2 filas + SUBTOTAL (líneas 0-3)
        List<FacturaConceptoResumen> conceptosEs = new ArrayList<>();
        parser.parseConceptosResumen(lines.subList(0, 4), conceptosEs, MoeveFacturaParser.InvoiceLocale.ES);
        assertThat(conceptosEs).hasSize(2);
        assertThat(conceptosEs).noneMatch(c -> c.getConceptoOriginal().equalsIgnoreCase("SUBTOTAL"));
        BigDecimal descuentoEs = conceptosEs.stream()
                .filter(c -> c.getConceptoOriginal().equals("DESCUENTOS TELEPEAJES"))
                .findFirst().orElseThrow().getImporte();
        assertThat(descuentoEs).isEqualByComparingTo(new BigDecimal("-18.04"));

        // Bloque PT: cabecera + 2 filas + SUBTOTAL (líneas 4-7)
        List<FacturaConceptoResumen> conceptosPt = new ArrayList<>();
        parser.parseConceptosResumen(lines.subList(4, 8), conceptosPt, MoeveFacturaParser.InvoiceLocale.PT);
        assertThat(conceptosPt).hasSize(2);
        assertThat(conceptosPt).noneMatch(c -> c.getConceptoOriginal().equalsIgnoreCase("SUBTOTAL"));
        BigDecimal descuentoPt = conceptosPt.stream()
                .filter(c -> c.getConceptoOriginal().equals("DESCONTO NA FATURA"))
                .findFirst().orElseThrow().getImporte();
        assertThat(descuentoPt).isEqualByComparingTo(new BigDecimal("-12.50"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // parseExtracto
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("parseExtracto reconoce el marcador de tarjeta Family A (*** MATRICULA/TARJETA ***) sin regresión")
    void parseExtractoFamilyANoRegresion() throws IOException {
        List<String> lines = loadFixture("familyA_basic.txt");
        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

        parser.parseExtracto(lines, tarjetaResumenes);

        assertThat(tarjetaResumenes).hasSize(1);
        TarjetaResumen tarjeta = tarjetaResumenes.get(0);
        assertThat(tarjeta.getAlias()).isEqualTo("11973654S");
        assertThat(tarjeta.getNumTarjeta()).isEqualTo("708011008022408411");
        assertThat(tarjeta.getOperaciones()).hasSize(1);

        Operacion op = tarjeta.getOperaciones().get(0);
        assertThat(op.getConceptoOriginal()).isEqualTo("DIESEL STAR");
        assertThat(op.getFechaHora()).isEqualTo(LocalDateTime.of(2025, 12, 5, 7, 45));
        assertThat(op.getImporteTotal()).isEqualByComparingTo(new BigDecimal("69.94"));
    }

    @Test
    @DisplayName("parseExtracto reconoce el marcador de tarjeta Family B (MATRICULA:X/PAN:Y, sin asteriscos) y su línea de operación")
    void parseExtractoFamilyBBasic() throws IOException {
        List<String> lines = loadFixture("familyB_basic.txt");
        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

        parser.parseExtracto(lines, tarjetaResumenes);

        assertThat(tarjetaResumenes).hasSize(1);
        TarjetaResumen tarjeta = tarjetaResumenes.get(0);
        assertThat(tarjeta.getAlias()).isEqualTo("03462425M");
        assertThat(tarjeta.getNumTarjeta()).isEqualTo("708011008022409211");
        assertThat(tarjeta.getOperaciones()).hasSize(1);

        Operacion op = tarjeta.getOperaciones().get(0);
        assertThat(op.getEstablecimiento()).isEqualTo("E.S. HENARES");
        assertThat(op.getConceptoOriginal()).isEqualTo("DIESEL STAR");
        assertThat(op.getFechaHora()).isEqualTo(LocalDateTime.of(2026, 3, 10, 19, 59, 30));
        assertThat(op.getCantidad()).isEqualByComparingTo(new BigDecimal("46.95"));
        assertThat(op.getPrecioUnitario()).isEqualByComparingTo(new BigDecimal("1.819"));
        assertThat(op.getPrecioIvaInc()).isEqualByComparingTo(new BigDecimal("85.40"));
        assertThat(op.getDtoTotal()).isEqualByComparingTo(new BigDecimal("-0.704"));
        assertThat(op.getImporteTotal()).isEqualByComparingTo(new BigDecimal("84.70"));
    }

    @Test
    @DisplayName("parseExtracto reconoce Family B en facturas PT (MATRICULA/PAN, fecha dd-MM-yyyy)")
    void parseExtractoFamilyBPt() throws IOException {
        List<String> lines = loadFixture("familyB_pt.txt");
        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

        parser.parseExtracto(lines, tarjetaResumenes);

        assertThat(tarjetaResumenes).hasSize(1);
        TarjetaResumen tarjeta = tarjetaResumenes.get(0);
        assertThat(tarjeta.getAlias()).isEqualTo("12AB34");
        assertThat(tarjeta.getNumTarjeta()).isEqualTo("708011008099001122");
        assertThat(tarjeta.getOperaciones()).hasSize(1);

        Operacion op = tarjeta.getOperaciones().get(0);
        assertThat(op.getEstablecimiento()).isEqualTo("POSTO CENTRAL");
        assertThat(op.getConceptoOriginal()).isEqualTo("GASOLEOS");
        assertThat(op.getFechaHora()).isEqualTo(LocalDateTime.of(2026, 4, 11, 8, 12, 5));
        assertThat(op.getImporteTotal()).isEqualByComparingTo(new BigDecimal("78.57"));
    }

    @Test
    @DisplayName("parseExtracto reconoce operaciones PT sin segundos en la hora (HH:mm real, no HH:mm:ss)")
    void parseExtractoFamilyBPtSinSegundos() throws IOException {
        List<String> lines = loadFixture("familyB_pt_extracto_sin_segundos.txt");
        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

        parser.parseExtracto(lines, tarjetaResumenes);

        assertThat(tarjetaResumenes).hasSize(1);
        TarjetaResumen tarjeta = tarjetaResumenes.get(0);
        assertThat(tarjeta.getOperaciones()).hasSize(1);

        Operacion op = tarjeta.getOperaciones().get(0);
        assertThat(op.getEstablecimiento()).isEqualTo("P.A. VILA REAL III");
        assertThat(op.getConceptoOriginal()).isEqualTo("GNA. SEM PB 95");
        assertThat(op.getFechaHora()).isEqualTo(LocalDateTime.of(2026, 6, 10, 20, 6));
        assertThat(op.getImporteTotal()).isEqualByComparingTo(new BigDecimal("55.52"));
    }

    @Test
    @DisplayName("parseExtracto reconoce operaciones de peaje Family A (\"OPERACIONES GESTION SERVICIOS ... USO RED PORTUGAL\", sin palabra clave de combustible)")
    void parseExtractoFamilyAPeaje() throws IOException {
        List<String> lines = loadFixture("familyA_extracto_peaje.txt");
        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

        parser.parseExtracto(lines, tarjetaResumenes);

        assertThat(tarjetaResumenes).hasSize(1);
        TarjetaResumen tarjeta = tarjetaResumenes.get(0);
        assertThat(tarjeta.getNumTarjeta()).isEqualTo("903338001814200107");
        assertThat(tarjeta.getOperaciones()).hasSize(1);

        Operacion op = tarjeta.getOperaciones().get(0);
        assertThat(op.getConceptoOriginal()).isEqualTo("USO RED PORTUGAL");
        assertThat(op.getFechaHora()).isEqualTo(LocalDateTime.of(2026, 3, 31, 16, 7));
        assertThat(op.getImporteTotal()).isEqualByComparingTo(new BigDecimal("1.21"));
    }

    @Test
    @DisplayName("parseExtracto reconoce peajes AUDASA: marcador de tarjeta sin espacio antes de \"***\" y operaciones con fecha dd.MM.yyyy (puntos) + hora con segundos")
    void parseExtractoFamilyAAudasaSinEspacioYFechaConPuntos() throws IOException {
        List<String> lines = loadFixture("familyA_extracto_audasa_sin_espacio.txt");
        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

        parser.parseExtracto(lines, tarjetaResumenes);

        assertThat(tarjetaResumenes).hasSize(1);
        TarjetaResumen tarjeta = tarjetaResumenes.get(0);
        assertThat(tarjeta.getNumTarjeta()).isEqualTo("7076460769900010");
        assertThat(tarjeta.getAlias()).isEqualTo("Y0997511S");
        assertThat(tarjeta.getOperaciones()).hasSize(2);

        Operacion op = tarjeta.getOperaciones().get(0);
        assertThat(op.getEstablecimiento()).isEqualTo("AUDASA Tui-Puxeiros");
        assertThat(op.getConceptoOriginal()).isEqualTo("PEAJE");
        assertThat(op.getFechaHora()).isEqualTo(LocalDateTime.of(2026, 2, 2, 5, 55, 0));
        assertThat(op.getImporteTotal()).isEqualByComparingTo(new BigDecimal("3.55"));
    }

    @Test
    @DisplayName("parseExtracto reconoce el marcador de tarjeta Family A en portugués (\"MATRICULA/CARTÃO\", no \"MATRICULA/TARJETA\")")
    void parseExtractoFamilyAPeajePt() throws IOException {
        List<String> lines = loadFixture("familyA_pt_extracto_cartao.txt");
        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

        parser.parseExtracto(lines, tarjetaResumenes);

        assertThat(tarjetaResumenes).hasSize(1);
        TarjetaResumen tarjeta = tarjetaResumenes.get(0);
        assertThat(tarjeta.getNumTarjeta()).isEqualTo("903338001814200008");
        assertThat(tarjeta.getOperaciones()).hasSize(1);

        Operacion op = tarjeta.getOperaciones().get(0);
        assertThat(op.getImporteTotal()).isEqualByComparingTo(new BigDecimal("135.95"));
    }

    @Test
    @DisplayName("parseExtracto reconoce el marcador de tarjeta aunque PDFBox extraiga '?' literal en vez de la tilde (fuente PT mal mapeada)")
    void parseExtractoFamilyAPeajePtConSignoInterrogacion() throws IOException {
        List<String> lines = loadFixture("familyA_pt_extracto_cartao_mangled.txt");
        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

        parser.parseExtracto(lines, tarjetaResumenes);

        assertThat(tarjetaResumenes).hasSize(1);
        TarjetaResumen tarjeta = tarjetaResumenes.get(0);
        assertThat(tarjeta.getNumTarjeta()).isEqualTo("903338001814200008");
        assertThat(tarjeta.getOperaciones()).hasSize(1);

        Operacion op = tarjeta.getOperaciones().get(0);
        assertThat(op.getImporteTotal()).isEqualByComparingTo(new BigDecimal("135.95"));
    }

    @Test
    @DisplayName("parseExtracto reconoce operaciones cuando el establecimiento se parte en 2 líneas y la fecha/hora/datos quedan solas en una tercera línea sin prefijo (bug real: se perdían las 4 operaciones enteras de la tarjeta)")
    void parseExtractoEstablecimientoPartidoSinPrefijoEnLineaDeOperacion() throws IOException {
        List<String> lines = loadFixture("familyB_establecimiento_partido_sin_prefijo.txt");
        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

        parser.parseExtracto(lines, tarjetaResumenes);

        assertThat(tarjetaResumenes).hasSize(1);
        TarjetaResumen tarjeta = tarjetaResumenes.get(0);
        assertThat(tarjeta.getOperaciones()).hasSize(4);

        Operacion primera = tarjeta.getOperaciones().get(0);
        assertThat(primera.getEstablecimiento()).isEqualTo("P.A. MARCO CANAVESES SOALHÕES");
        assertThat(primera.getConceptoOriginal()).isEqualTo("GASOLEO");
        assertThat(primera.getFechaHora()).isEqualTo(LocalDateTime.of(2026, 6, 5, 16, 23));
        assertThat(primera.getCantidad()).isEqualByComparingTo(new BigDecimal("28.56"));
        assertThat(primera.getImporteTotal()).isEqualByComparingTo(new BigDecimal("52.30"));

        Operacion ultima = tarjeta.getOperaciones().get(3);
        assertThat(ultima.getEstablecimiento()).isEqualTo("P.A. MARCO CANAVESES SOALHÕES");
        assertThat(ultima.getFechaHora()).isEqualTo(LocalDateTime.of(2026, 6, 26, 16, 29));
        assertThat(ultima.getImporteTotal()).isEqualByComparingTo(new BigDecimal("55.61"));
    }

    @Test
    @DisplayName("parseExtracto: establecimiento partido en 2 líneas no contamina la operación normal (de una sola línea) siguiente")
    void parseExtractoEstablecimientoPartidoNoContaminaOperacionSiguiente() throws IOException {
        List<String> lines = loadFixture("familyB_establecimiento_partido_mezclado.txt");
        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

        parser.parseExtracto(lines, tarjetaResumenes);

        assertThat(tarjetaResumenes).hasSize(1);
        TarjetaResumen tarjeta = tarjetaResumenes.get(0);
        assertThat(tarjeta.getOperaciones()).hasSize(3);

        assertThat(tarjeta.getOperaciones().get(0).getEstablecimiento()).isEqualTo("P.A. CONSTANCE");
        assertThat(tarjeta.getOperaciones().get(0).getImporteTotal()).isEqualByComparingTo(new BigDecimal("61.05"));

        assertThat(tarjeta.getOperaciones().get(1).getEstablecimiento()).isEqualTo("P.A. MARCO CANAVESES SOALHÕES");
        assertThat(tarjeta.getOperaciones().get(1).getImporteTotal()).isEqualByComparingTo(new BigDecimal("66.52"));

        assertThat(tarjeta.getOperaciones().get(2).getEstablecimiento()).isEqualTo("P.A. CONSTANCE");
        assertThat(tarjeta.getOperaciones().get(2).getImporteTotal()).isEqualByComparingTo(new BigDecimal("48.23"));
    }

    @Test
    @DisplayName("parseExtracto: importeTotal siempre es el último token numérico de la línea, sea cual sea el número de campos intermedios")
    void parseExtractoImporteTotalEsUltimoTokenNumerico() {
        List<String> lines = List.of(
                "*** MATRICULA/TARJETA 99999999X *** 708011008099999999 ***",
                "12345678901 DIESEL 5,50 3,20");
        List<TarjetaResumen> tarjetaResumenes = new ArrayList<>();

        parser.parseExtracto(lines, tarjetaResumenes);

        assertThat(tarjetaResumenes).hasSize(1);
        List<Operacion> operaciones = tarjetaResumenes.get(0).getOperaciones();
        assertThat(operaciones).hasSize(1);
        assertThat(operaciones.get(0).getImporteTotal()).isEqualByComparingTo(new BigDecimal("3.20"));
    }
}
