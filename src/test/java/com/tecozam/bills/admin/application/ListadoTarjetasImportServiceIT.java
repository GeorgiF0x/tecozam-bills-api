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
    @DisplayName("Cepsa: import del listado-mini crea 3 tarjetas y 2 VIATs; 1 fila ignorada (concepto desconocido)")
    void importCepsaMiniGeneraReporteCorrecto() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa-mini.xlsx");

        ImportTarjetasReportDTO report = importService.importar(file, "CEPSA");

        assertThat(report.tarjetasCreadas() + report.tarjetasExistentes())
                .as("3 tarjetas combustible + 1 default desconocido = 4")
                .isEqualTo(4);
        assertThat(report.viatsCreados() + report.viatsExistentes())
                .as("2 VIATs (AUTOPISTAS + PORTAGEM)")
                .isEqualTo(2);
        assertThat(report.filasIgnoradas())
                .as("La fila con CONCEPTO-NO-CONOCIDO se procesa como TARJETA y se cuenta en filasIgnoradas")
                .isEqualTo(1);
        assertThat(report.duracionMs()).isPositive();
    }

    @Test
    @DisplayName("Cepsa: las tarjetas Cepsa (7080*) NO acaban en el maestro VIAT (regresión BILLS-04)")
    void cepsaTarjetasNoVanAlMaestroViat() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa-mini.xlsx");

        importService.importar(file, "CEPSA");

        assertThat(viatRepo.findByCodigo("708011008022409211")).isEmpty();
        assertThat(viatRepo.findByCodigo("708011008022416612")).isEmpty();
        assertThat(viatRepo.findByCodigo("708011008022414419")).isEmpty();
        assertThat(tarjetaRepo.findByNumeroTarjeta("708011008022409211")).isPresent();
        assertThat(viatRepo.findByCodigo("7076460769901026"))
                .as("Los VIATs sí van al maestro VIAT")
                .isPresent();
    }

    @Test
    @DisplayName("Repsol: número se persiste en forma canónica (15 dígitos sin prefijo 0007)")
    void repsolPersisteNumeroCanonico() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.repsolMini(), "repsol-mini.xlsx");

        importService.importar(file, "REPSOL");

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
        importService.importar(toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa.xlsx"), "CEPSA");
        long tarjetasTrasPrimero = tarjetaRepo.count();
        long viatsTrasPrimero = viatRepo.count();

        ImportTarjetasReportDTO segundo = importService.importar(
                toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa.xlsx"), "CEPSA");

        assertThat(segundo.tarjetasCreadas()).isZero();
        assertThat(segundo.viatsCreados()).isZero();
        assertThat(segundo.tarjetasExistentes()).isPositive();
        assertThat(segundo.viatsExistentes()).isPositive();
        assertThat(tarjetaRepo.count()).isEqualTo(tarjetasTrasPrimero);
        assertThat(viatRepo.count()).isEqualTo(viatsTrasPrimero);
    }

    @Test
    @DisplayName("Proveedor desconocido lanza excepción amigable (no 500)")
    void proveedorDesconocidoLanzaIllegalArg() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa.xlsx");
        assertThatThrownBy(() -> importService.importar(file, "GALP"))
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
