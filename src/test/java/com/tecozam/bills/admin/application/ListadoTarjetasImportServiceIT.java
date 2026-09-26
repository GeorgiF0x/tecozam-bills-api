package com.tecozam.bills.admin.application;

import com.tecozam.bills.admin.dto.ImportTarjetasReportDTO;
import com.tecozam.bills.shared.fixtures.XlsxFixtureBuilder;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ListadoTarjetasImportServiceIT {

    @Autowired
    private ListadoTarjetasImportService importService;

    @Autowired
    private TarjetaRepository tarjetaRepo;

    @Autowired
    private ViatRepository viatRepo;

    @BeforeEach
    void limpiarRegistrosDeFixtures() {
        // Tarjetas que las fixtures meten (canonical + posible variante no-canónica de smoke tests previos)
        java.util.List.of(
                "078833651671188", "078833651671246", "078833651671295",
                "078833651670503", "078833651671402",
                "0007078833651671188", "0007078833651671246", "0007078833651671295",
                "0007078833651670503", "0007078833651671402",
                "708011008022409211", "708011008022416612", "708011008022414419",
                "708011008022414013"
        ).forEach(num -> tarjetaRepo.findByNumeroTarjeta(num).ifPresent(tarjetaRepo::delete));
        java.util.List.of(
                "0007078833651670503",
                "7076460769901026", "7076460769900077",
                "708011008022409211", "708011008022416612", "708011008022414419"
        ).forEach(cod -> viatRepo.findByCodigo(cod).ifPresent(viatRepo::delete));
    }

    @Test
    @DisplayName("Cepsa (esViat=false): TODAS las filas del listado-mini se importan como tarjetas, incluidas las que antes se adivinaban como VIAT por el concepto")
    void importCepsaMiniConEsViatFalseNoCreaNingunViat() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa-mini.xlsx");

        ImportTarjetasReportDTO report = importService.importar(file, "CEPSA", false);

        assertThat(report.viatsCreados())
                .as("esViat=false: nunca se crea ningún VIAT, sin importar el texto del concepto")
                .isZero();
        assertThat(report.tarjetasCreadas() + report.tarjetasExistentes())
                .as("Todas las filas parseables (combustible + peaje + desconocido) se importan como tarjeta")
                .isEqualTo(6);
        assertThat(report.filasIgnoradas())
                .as("La fila con CONCEPTO-NO-CONOCIDO se sigue reportando (dato de calidad, no depende del tipo)")
                .isEqualTo(1);
        assertThat(report.duracionMs()).isPositive();
    }

    @Test
    @DisplayName("Cepsa (esViat=true): TODAS las filas del listado-mini se importan como VIAT")
    void importCepsaMiniConEsViatTrueCreaSoloViats() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa-mini.xlsx");

        ImportTarjetasReportDTO report = importService.importar(file, "CEPSA", true);

        assertThat(report.tarjetasCreadas())
                .as("esViat=true: nunca se crea ninguna tarjeta, sin importar el texto del concepto")
                .isZero();
        assertThat(report.viatsCreados() + report.viatsExistentes())
                .as("Todas las filas parseables se importan como VIAT")
                .isEqualTo(6);
    }

    @Test
    @DisplayName("Cepsa: las tarjetas Cepsa (7080*) NO acaban en el maestro VIAT salvo que el admin lo declare explícitamente (regresión BILLS-04 y del bug de auto-clasificación)")
    void cepsaTarjetasNoVanAlMaestroViatSalvoDeclaracionExplicita() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa-mini.xlsx");

        importService.importar(file, "CEPSA", false);

        assertThat(viatRepo.findByCodigo("708011008022409211")).isEmpty();
        assertThat(viatRepo.findByCodigo("708011008022416612")).isEmpty();
        assertThat(viatRepo.findByCodigo("708011008022414419")).isEmpty();
        assertThat(tarjetaRepo.findByNumeroTarjeta("708011008022409211")).isPresent();
        assertThat(tarjetaRepo.findByNumeroTarjeta("7076460769901026"))
                .as("Con esViat=false, incluso la fila de peaje real se importa como tarjeta")
                .isPresent();
    }

    @Test
    @DisplayName("Repsol: número se persiste en forma canónica (15 dígitos sin prefijo 0007)")
    void repsolPersisteNumeroCanonico() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.repsolMini(), "repsol-mini.xlsx");

        importService.importar(file, "REPSOL", false);

        assertThat(tarjetaRepo.findByNumeroTarjeta("078833651671188"))
                .as("Excel guardaba 0007078833651671188; canónico = 15 dígitos sin prefijo")
                .isPresent();
        assertThat(tarjetaRepo.findByNumeroTarjeta("0007078833651671188"))
                .as("La forma no canónica NO se persiste")
                .isEmpty();
    }

    @Test
    @DisplayName("Idempotencia: segundo import del mismo Excel no duplica ningún registro")
    void segundoImportDelMismoExcelNoDuplica() throws Exception {
        importService.importar(toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa.xlsx"), "CEPSA", false);
        long tarjetasTrasPrimero = tarjetaRepo.count();
        long viatsTrasPrimero = viatRepo.count();

        ImportTarjetasReportDTO segundo = importService.importar(
                toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa.xlsx"), "CEPSA", false);

        assertThat(segundo.tarjetasCreadas()).isZero();
        assertThat(segundo.viatsCreados()).isZero();
        assertThat(segundo.tarjetasExistentes()).isPositive();
        assertThat(tarjetaRepo.count()).isEqualTo(tarjetasTrasPrimero);
        assertThat(viatRepo.count()).isEqualTo(viatsTrasPrimero);
    }

    @Test
    @DisplayName("Proveedor desconocido lanza excepción amigable (no 500)")
    void proveedorDesconocidoLanzaIllegalArg() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa.xlsx");
        assertThatThrownBy(() -> importService.importar(file, "GALP", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GALP");
    }

    private static MultipartFile toMultipart(Workbook wb, String filename) throws Exception {
        try (wb; ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            wb.write(baos);
            return new MockMultipartFile(
                    "file", filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baos.toByteArray());
        }
    }
}
