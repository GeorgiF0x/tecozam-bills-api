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
}
