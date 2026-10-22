package com.tecozam.bills.factura.infrastructure.parser;

import com.tecozam.bills.factura.application.FacturaImportValidator;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepsolFacturaParserTest {

    private final RepsolFacturaParser parser = new RepsolFacturaParser();

    private static List<String> loadFixture(String filename) throws IOException {
        try (InputStream is = RepsolFacturaParserTest.class.getResourceAsStream("/fixtures/repsol/" + filename)) {
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

    @Test
    @DisplayName("parseConceptosResumen extrae conceptos reales con y sin columna de cantidad, tipos 10% y 21% mezclados, y filas DESCUENTO en negativo")
    void parseConceptosResumenExtraeConceptosReales() throws IOException {
        List<String> lines = loadFixture("conceptos_reales.txt");
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();

        parser.parseConceptosResumen(lines, conceptos);

        assertThat(conceptos).hasSize(9);

        FacturaConceptoResumen autopistas = conceptos.stream()
                .filter(c -> c.getConceptoOriginal().equals("AUTOPISTAS"))
                .findFirst().orElseThrow();
        assertThat(autopistas.getCantidad()).isNull();
        assertThat(autopistas.getBaseImponible()).isEqualByComparingTo(new BigDecimal("254.75"));
        assertThat(autopistas.getTipoIva()).isEqualByComparingTo(new BigDecimal("21.00"));
        assertThat(autopistas.getCuotaIva()).isEqualByComparingTo(new BigDecimal("53.50"));
        assertThat(autopistas.getImporte()).isEqualByComparingTo(new BigDecimal("308.25"));

        FacturaConceptoResumen efitec = conceptos.stream()
                .filter(c -> c.getConceptoOriginal().equals("EFITEC 95 N (L)"))
                .findFirst().orElseThrow();
        assertThat(efitec.getCantidad()).isEqualByComparingTo(new BigDecimal("1063.69"));
        assertThat(efitec.getBaseImponible()).isEqualByComparingTo(new BigDecimal("1465.42"));
        assertThat(efitec.getTipoIva()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(efitec.getCuotaIva()).isEqualByComparingTo(new BigDecimal("146.54"));
        assertThat(efitec.getImporte()).isEqualByComparingTo(new BigDecimal("1611.96"));

        List<FacturaConceptoResumen> descuentos = conceptos.stream()
                .filter(c -> c.getConceptoOriginal().equals("DESCUENTO CTS DESPUES IMPUESTO"))
                .toList();
        assertThat(descuentos).hasSize(2);
        assertThat(descuentos.get(0).getImporte()).isEqualByComparingTo(new BigDecimal("-61.18"));
    }

    @Test
    @DisplayName("parseCabecera con línea PT de 4 tokens 'em Euros' descarta el token líder y toma los últimos 3")
    void parseCabeceraTotalesPortuguesDescartaTokenLider() throws IOException {
        List<String> lines = loadFixture("pt_totals_em.txt");
        Factura.FacturaBuilder builder = Factura.builder();

        parser.parseCabecera(lines, builder);
        Factura factura = builder.build();

        assertThat(factura.getBaseImponible()).isEqualByComparingTo(new BigDecimal("1193.21"));
        assertThat(factura.getTotalIva()).isEqualByComparingTo(new BigDecimal("274.45"));
        assertThat(factura.getTotalFactura()).isEqualByComparingTo(new BigDecimal("1467.66"));
    }

    @Test
    @DisplayName("parseCabecera con línea ES de 3 tokens 'en Euros' sigue funcionando igual (regresión)")
    void parseCabeceraTotalesEspanolesSigueFuncionando() throws IOException {
        List<String> lines = loadFixture("conceptos_reales.txt");
        Factura.FacturaBuilder builder = Factura.builder();

        parser.parseCabecera(lines, builder);
        Factura factura = builder.build();

        assertThat(factura.getBaseImponible()).isEqualByComparingTo(new BigDecimal("33589.24"));
        assertThat(factura.getTotalIva()).isEqualByComparingTo(new BigDecimal("3446.19"));
        assertThat(factura.getTotalFactura()).isEqualByComparingTo(new BigDecimal("37035.43"));
    }

    @Test
    @DisplayName("parseConceptosResumen reconoce tipo IVA 23% (tasa estándar portuguesa)")
    void parseConceptosResumenReconoceTipo23PorCiento() throws IOException {
        List<String> lines = loadFixture("pt_concepto_23.txt");
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();

        parser.parseConceptosResumen(lines, conceptos);

        assertThat(conceptos).hasSize(1);
        FacturaConceptoResumen c = conceptos.get(0);
        assertThat(c.getTipoIva()).isEqualByComparingTo(new BigDecimal("23.00"));
        assertThat(c.getBaseImponible()).isEqualByComparingTo(new BigDecimal("1193.21"));
        assertThat(c.getCuotaIva()).isEqualByComparingTo(new BigDecimal("274.44"));
        assertThat(c.getImporte()).isEqualByComparingTo(new BigDecimal("1467.65"));
    }

    @Test
    @DisplayName("esDocumentoLiquidacion detecta documentos de liquidación (sin Núm. Factura, con 'Total en Euros')")
    void esDocumentoLiquidacionDetectaDocumentoLiquidacion() throws IOException {
        assertThat(parser.esDocumentoLiquidacion(loadFixture("liquidacion_header.txt"))).isTrue();
        assertThat(parser.esDocumentoLiquidacion(loadFixture("conceptos_reales.txt"))).isFalse();
        assertThat(parser.esDocumentoLiquidacion(loadFixture("pt_totals_em.txt"))).isFalse();
    }

    @Test
    @DisplayName("aplicarCabeceraLiquidacion genera numFactura sintético y totales sin desglose de IVA")
    void aplicarCabeceraLiquidacionGeneraNumFacturaSinteticoYTotales() throws IOException {
        List<String> lines = loadFixture("liquidacion_header.txt");
        Factura.FacturaBuilder builder = Factura.builder();

        parser.aplicarCabeceraLiquidacion(lines, builder);
        Factura factura = builder.build();

        assertThat(factura.getNumFactura()).isEqualTo("LIQ-31012026-53,35");
        assertThat(factura.getTotalFactura()).isEqualByComparingTo(new BigDecimal("53.35"));
        assertThat(factura.getBaseImponible()).isEqualByComparingTo(new BigDecimal("53.35"));
        assertThat(factura.getTotalIva()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("aplicarCabeceraLiquidacion usa 'Fecha de operación ... AL' como fallback cuando no hay línea 'Lugar y Fecha' (variante PT real: 'Nota de Liquidação')")
    void aplicarCabeceraLiquidacionUsaFallbackDePeriodoSiFaltaLugarYFecha() throws IOException {
        List<String> lines = loadFixture("liquidacion_header_sin_lugar_fecha.txt");
        Factura.FacturaBuilder builder = Factura.builder();

        parser.aplicarCabeceraLiquidacion(lines, builder);
        Factura factura = builder.build();

        assertThat(factura.getNumFactura()).isEqualTo("LIQ-31012026-18,65");
        assertThat(factura.getTotalFactura()).isEqualByComparingTo(new BigDecimal("18.65"));
    }

    @Test
    @DisplayName("parseConceptosResumen en modo liquidación extrae fila de 2 columnas concepto+importe")
    void parseConceptosResumenLiquidacionExtraeDosColumnas() throws IOException {
        List<String> lines = loadFixture("liquidacion_conceptos.txt");
        List<FacturaConceptoResumen> conceptos = new ArrayList<>();

        parser.parseConceptosResumen(lines, conceptos, true);

        assertThat(conceptos).hasSize(1);
        FacturaConceptoResumen c = conceptos.get(0);
        assertThat(c.getConceptoOriginal()).isEqualTo("TIENDA");
        assertThat(c.getImporte()).isEqualByComparingTo(new BigDecimal("55.11"));
        assertThat(c.getBaseImponible()).isEqualByComparingTo(new BigDecimal("55.11"));
        assertThat(c.getTipoIva()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(c.getCuotaIva()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("aplicarCabeceraLiquidacion usa el Núm. Doc. Liq. real cuando está impreso, y suma el total de TODAS las tarjetas (bug real: solo sumaba la primera)")
    void aplicarCabeceraLiquidacionUsaNumDocLiqRealYSumaTodasLasTarjetas() throws IOException {
        List<String> lines = loadFixture("liquidacion_multitarjeta.txt");
        Factura.FacturaBuilder builder = Factura.builder();

        parser.aplicarCabeceraLiquidacion(lines, builder);
        Factura factura = builder.build();

        assertThat(factura.getNumFactura()).isEqualTo("NLC260134917");
        assertThat(factura.getTotalFactura()).isEqualByComparingTo(new BigDecimal("61.96"));
        assertThat(factura.getBaseImponible()).isEqualByComparingTo(new BigDecimal("61.96"));
    }

    @Test
    @DisplayName("aplicarCabeceraLiquidacion conserva el Núm. Doc. Liq. completo aunque tenga un espacio interno (bug real: factura NLPX/00055916, \"Núm. Doc. Liq. NL NLPX/00055916\" se truncaba a \"NL\" porque el patrón solo capturaba el primer token no-blanco)")
    void aplicarCabeceraLiquidacionConservaNumDocLiqConEspacioInterno() throws IOException {
        List<String> lines = loadFixture("liquidacion_nlpx_referencia_corta.txt");
        Factura.FacturaBuilder builder = Factura.builder();

        parser.aplicarCabeceraLiquidacion(lines, builder);
        Factura factura = builder.build();

        assertThat(factura.getNumFactura()).isEqualTo("NL NLPX/00055916");
    }

    @Test
    @DisplayName("parseOperaciones reconoce operaciones de peaje con referencia corta (3-4 dígitos, p. ej. \"0004\"/\"0019\"), no solo el formato largo habitual de 6-8 dígitos (bug real: factura NLPX/00055916, 9 de 10 operaciones de peaje AUTOESTRADA se perdían enteras porque su referencia no llegaba a 6 dígitos)")
    void parseOperacionesReconoceReferenciaCortaDePeaje() throws IOException {
        List<String> lines = loadFixture("liquidacion_nlpx_referencia_corta.txt");
        List<TarjetaResumen> tarjetas = new ArrayList<>();

        parser.parseOperaciones(lines, tarjetas, 2026);

        assertThat(tarjetas).hasSize(2);

        TarjetaResumen benitogc = tarjetas.get(0);
        assertThat(benitogc.getOperaciones()).hasSize(2);
        Operacion autoestrada1 = benitogc.getOperaciones().get(1);
        assertThat(autoestrada1.getReferencia()).isEqualTo("0004");
        assertThat(autoestrada1.getImporteTotal()).isEqualByComparingTo(new BigDecimal("2.50"));

        TarjetaResumen pjnunes = tarjetas.get(1);
        assertThat(pjnunes.getOperaciones()).hasSize(1);
        Operacion autoestrada2 = pjnunes.getOperaciones().get(0);
        assertThat(autoestrada2.getReferencia()).isEqualTo("0019");
        assertThat(autoestrada2.getImporteTotal()).isEqualByComparingTo(new BigDecimal("2.45"));
    }

    @Test
    @DisplayName("parseOperaciones clasifica \"DESCUENTOS TELEPEAJES\" como DESCUENTO, no como PEAJE, aunque contenga la subcadena \"PEAJE\" (fix preventivo: mismo bug ya confirmado y arreglado en MoeveFacturaParser.resolveConceptoMoeve, el check de PEAJE/AUTOPISTA se comprobaba antes que el de DESCUENTO)")
    void parseOperacionesDescuentoTelepeajesNoSeConfundeConPeajePorLaSubcadena() throws IOException {
        List<String> lines = loadFixture("liquidacion_descuento_telepeajes_contiene_peaje.txt");
        List<TarjetaResumen> tarjetas = new ArrayList<>();

        parser.parseOperaciones(lines, tarjetas, 2026);

        assertThat(tarjetas).hasSize(1);
        Operacion op = tarjetas.get(0).getOperaciones().get(0);
        assertThat(op.getConceptoUnificado())
                .isEqualTo(com.tecozam.bills.shared.domain.enums.ConceptoUnificado.DESCUENTO.name());
        assertThat(op.getImporteTotal()).isEqualByComparingTo(new BigDecimal("-18.04"));
    }

    @Test
    @DisplayName("parseOperaciones extrae conductor limpio (sin arrastrar la palabra 'Conductor'), matrícula con espacio, y separa establecimiento aunque 'E.S.' venga pegado al nombre o ausente (bugs reales de la factura Solred de agosto 2026)")
    void parseOperacionesExtraeConductorMatriculaYEstablecimientoCorrectamente() throws IOException {
        List<String> lines = loadFixture("liquidacion_multitarjeta.txt");
        List<TarjetaResumen> tarjetas = new ArrayList<>();

        parser.parseOperaciones(lines, tarjetas, 2026);

        assertThat(tarjetas).hasSize(2);

        TarjetaResumen gomez = tarjetas.get(0);
        assertThat(gomez.getAlias()).isEqualTo("C. GOMEZ");
        assertThat(gomez.getConductor()).isNull();
        assertThat(gomez.getOperaciones()).hasSize(4);

        Operacion cafestore = gomez.getOperaciones().get(0);
        assertThat(cafestore.getConceptoOriginal()).isEqualTo("TIENDA");
        assertThat(cafestore.getEstablecimiento()).isEqualTo("CAFESTORE, S.A.U. CR.A-52");

        Operacion espinosa = gomez.getOperaciones().get(1);
        assertThat(espinosa.getConceptoOriginal()).isEqualTo("TIENDA");
        assertThat(espinosa.getEstablecimiento()).isEqualTo("ESPINOSA N-VI PK 118,");

        TarjetaResumen vicente = tarjetas.get(1);
        assertThat(vicente.getAlias()).isEqualTo("M.VICENTE");
        assertThat(vicente.getConductor()).isEqualTo("MANUEL VICENTE");
        assertThat(vicente.getOperaciones()).hasSize(2);
    }

    @Test
    @DisplayName("parseOperaciones no asigna cantidad (litros) a conceptos sin litros reales como TIENDA (bug real: 12,02 importe se guardaba como 12,02 litros y disparaba LITROS_NO_COINCIDEN al cotejar)")
    void parseOperacionesNoAsignaCantidadAConceptosSinLitros() throws IOException {
        List<String> lines = loadFixture("liquidacion_multitarjeta.txt");
        List<TarjetaResumen> tarjetas = new ArrayList<>();

        parser.parseOperaciones(lines, tarjetas, 2026);

        Operacion espinosa = tarjetas.get(0).getOperaciones().get(1);
        assertThat(espinosa.getEstablecimiento()).isEqualTo("ESPINOSA N-VI PK 118,");
        assertThat(espinosa.getCantidad()).isNull();
        assertThat(espinosa.getImporteTotal()).isEqualByComparingTo(new BigDecimal("12.02"));
    }

    @Test
    @DisplayName("parseOperaciones no asigna precioUnitario a un campo de la tirada numerica cuando faltan columnas intermedias "
            + "(bug real factura A260638472: pdftotext/PDFBox elimina celdas vacias y desplaza los indices, "
            + "haciendo que precioUnitario tomara el valor de 'importe' (ej. 81,91) en vez de un precio/litro plausible, "
            + "disparando 1192 avisos falsos de 'precio fuera de rango' en una sola factura real)")
    void parseOperacionesNoAsignaPrecioUnitarioConCamposIntermediosAmbiguos() throws IOException {
        List<String> lines = loadFixture("operaciones_campos_parciales.txt");
        List<TarjetaResumen> tarjetas = new ArrayList<>();

        parser.parseOperaciones(lines, tarjetas, 2026);

        assertThat(tarjetas).hasSize(1);
        List<Operacion> ops = tarjetas.get(0).getOperaciones();
        assertThat(ops).hasSize(9);

        // Fila con 7 valores numericos en la tirada: cantidad, precioNeto,
        // precioIvaInc, importe, dto%, dtoTotal, importeTotal. Solo los 3
        // primeros (ancla por delante) y el ultimo (ancla por detras) son
        // posicionalmente inambiguos; el resto no se puede repartir con
        // certeza entre precioUnitario/precioAplicado/importe/dto%/dtoTotal/
        // bonificacion sin saber cual columna intermedia quedo vacia.
        Operacion diesel1 = ops.get(0);
        assertThat(diesel1.getCantidad()).isEqualByComparingTo(new BigDecimal("47.93"));
        assertThat(diesel1.getPrecioNeto()).isEqualByComparingTo(new BigDecimal("1.709"));
        assertThat(diesel1.getPrecioIvaInc()).isEqualByComparingTo(new BigDecimal("1.709"));
        assertThat(diesel1.getPrecioUnitario()).isNull();
        assertThat(diesel1.getImporteTotal()).isEqualByComparingTo(new BigDecimal("75.20"));

        Operacion adblue = ops.get(4);
        assertThat(adblue.getConceptoUnificado()).isEqualTo(com.tecozam.bills.shared.domain.enums.ConceptoUnificado.ADBLUE.name());
        assertThat(adblue.getCantidad()).isEqualByComparingTo(new BigDecimal("12.62"));
        assertThat(adblue.getPrecioIvaInc()).isEqualByComparingTo(new BigDecimal("1.259"));
        assertThat(adblue.getPrecioUnitario()).isNull();
        assertThat(adblue.getImporteTotal()).isEqualByComparingTo(new BigDecimal("13.37"));
    }

    @Test
    @DisplayName("parseOperaciones SI reparte importe/dto%/dtoTotal cuando la tirada trae exactamente 7 valores "
            + "(forma verificada con descuento real: la resta importe-dtoTotal cuadra EXACTAMENTE con importeTotal "
            + "en dos operaciones reales distintas, 15,89-2,52=13,37 y 81,91-6,71=75,20 — no es una posicion adivinada) "
            + "y el campo importe cuando trae exactamente 5 (forma sin descuento, importe==importeTotal)")
    void parseOperacionesRepartePorcentajeDeDescuentoCuandoLaFormaEsInambigua() throws IOException {
        List<String> lines = loadFixture("operaciones_campos_parciales.txt");
        List<TarjetaResumen> tarjetas = new ArrayList<>();

        parser.parseOperaciones(lines, tarjetas, 2026);

        List<Operacion> ops = tarjetas.get(0).getOperaciones();
        assertThat(ops).hasSize(9);

        Operacion adblue = ops.get(4);
        assertThat(adblue.getImporte()).isEqualByComparingTo(new BigDecimal("15.89"));
        assertThat(adblue.getDtoPorcentaje()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(adblue.getDtoTotal()).isEqualByComparingTo(new BigDecimal("2.52"));

        Operacion dieselSinDescuento = ops.get(5);
        assertThat(dieselSinDescuento.getCantidad()).isEqualByComparingTo(new BigDecimal("48.25"));
        assertThat(dieselSinDescuento.getImporte()).isEqualByComparingTo(new BigDecimal("73.29"));
        assertThat(dieselSinDescuento.getDtoTotal()).isNull();
        assertThat(dieselSinDescuento.getImporteTotal()).isEqualByComparingTo(new BigDecimal("73.29"));
    }

    @Test
    @DisplayName("parseOperaciones no confunde un punto kilometrico con coma decimal embebido en el establecimiento "
            + "('ESVERINSA CR N-525, 132,1') con un campo numerico real cuando la fila no trae columna de Kms que actue "
            + "de freno (bug real factura A260638472: cantidad se leia como 132,1 en vez de 25,92/8,27, disparando "
            + "'importe no cuadra' con un calculado disparatado de 220,47€ y 112,29€)")
    void parseOperacionesNoConfundePuntoKilometricoDeEstablecimientoConCampoNumerico() throws IOException {
        List<String> lines = loadFixture("operaciones_campos_parciales.txt");
        List<TarjetaResumen> tarjetas = new ArrayList<>();

        parser.parseOperaciones(lines, tarjetas, 2026);

        List<Operacion> ops = tarjetas.get(0).getOperaciones();
        Operacion diesel = ops.get(6);
        // El punto kilometrico "132,1" se reconoce ahora como el valor numerico
        // sobrante que es (via la identidad aritmetica) y se descarta del todo,
        // en vez de quedar colgado como texto dentro del establecimiento.
        assertThat(diesel.getEstablecimiento()).isEqualTo("ESVERINSA CR N-525,");
        assertThat(diesel.getCantidad()).isEqualByComparingTo(new BigDecimal("25.92"));
        assertThat(diesel.getPrecioIvaInc()).isEqualByComparingTo(new BigDecimal("1.669"));
        assertThat(diesel.getImporteTotal()).isEqualByComparingTo(new BigDecimal("43.26"));

        Operacion adbluePuntoKm = ops.get(7);
        assertThat(adbluePuntoKm.getCantidad()).isEqualByComparingTo(new BigDecimal("8.27"));
        assertThat(adbluePuntoKm.getPrecioIvaInc()).isEqualByComparingTo(new BigDecimal("0.85"));
        assertThat(adbluePuntoKm.getImporteTotal()).isEqualByComparingTo(new BigDecimal("5.38"));
    }

    @Test
    @DisplayName("parseOperaciones NO descarta la cantidad real cuando un codigo de carretera SIN punto kilometrico "
            + "detras precede directamente al campo numerico real ('CTR N-634, 36,85 ...' — bug real de una correccion "
            + "anterior demasiado agresiva: bloqueaba por texto cualquier numero tras un codigo de carretera, incluida "
            + "la cantidad legitima, en vez de verificar la identidad aritmetica)")
    void parseOperacionesNoDescartaCantidadRealTrasCodigoCarreteraSinPuntoKilometrico() throws IOException {
        List<String> lines = loadFixture("operaciones_campos_parciales.txt");
        List<TarjetaResumen> tarjetas = new ArrayList<>();

        parser.parseOperaciones(lines, tarjetas, 2026);

        List<Operacion> ops = tarjetas.get(0).getOperaciones();
        Operacion diesel = ops.get(8);
        assertThat(diesel.getEstablecimiento()).isEqualTo("A.S. LA RETAMA CTR N-634,");
        assertThat(diesel.getCantidad()).isEqualByComparingTo(new BigDecimal("36.85"));
        assertThat(diesel.getPrecioIvaInc()).isEqualByComparingTo(new BigDecimal("1.729"));
        assertThat(diesel.getImporteTotal()).isEqualByComparingTo(new BigDecimal("63.71"));
    }

    @Test
    @DisplayName("FacturaImportValidator ya no marca precio/litro fuera de rango, NI 'importe no cuadra', para estas "
            + "operaciones reales con descuento (regresion directa del bug de 1192 avisos falsos: antes precioUnitario "
            + "quedaba en ~81,91€ y el descuento real se interpretaba como una discrepancia de parser)")
    void operacionesConCamposParciales_noDisparanAvisosFalsos() throws IOException {
        List<String> lines = loadFixture("operaciones_campos_parciales.txt");
        List<TarjetaResumen> tarjetas = new ArrayList<>();
        parser.parseOperaciones(lines, tarjetas, 2026);

        Factura factura = Factura.builder()
                .totalFactura(new BigDecimal("470.11"))
                .tarjetaResumenes(tarjetas)
                .build();
        tarjetas.forEach(tr -> tr.setFactura(factura));

        FacturaImportValidator validator = new FacturaImportValidator();
        List<String> avisos = validator.validar(factura);

        assertThat(avisos).noneMatch(a -> a.contains("fuera de rango plausible"));
        assertThat(avisos).noneMatch(a -> a.contains("no cuadra con cantidad x precio"));
    }
}
