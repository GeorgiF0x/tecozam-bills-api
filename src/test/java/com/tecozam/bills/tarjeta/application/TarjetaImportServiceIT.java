package com.tecozam.bills.tarjeta.application;

import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.tarjeta.dto.ImportTarjetasAltaReportDTO;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TarjetaImportServiceIT {

    private static final String NUMERO_1 = "9900112233";
    private static final String PROVEEDOR_ACTIVO = "SECTEST_PROV_ACTIVO";
    private static final String PROVEEDOR_INACTIVO = "SECTEST_PROV_INACTIVO";

    @Autowired
    private TarjetaImportService importService;

    @Autowired
    private TarjetaRepository tarjetaRepository;

    @Autowired
    private ProveedorRepository proveedorRepository;

    @BeforeEach
    void sembrarProveedores() {
        proveedorRepository.findByCodigo(PROVEEDOR_ACTIVO).orElseGet(() ->
                proveedorRepository.save(Proveedor.builder()
                        .codigo(PROVEEDOR_ACTIVO).nombre(PROVEEDOR_ACTIVO).activo(true).build()));
        proveedorRepository.findByCodigo(PROVEEDOR_INACTIVO).orElseGet(() ->
                proveedorRepository.save(Proveedor.builder()
                        .codigo(PROVEEDOR_INACTIVO).nombre(PROVEEDOR_INACTIVO).activo(false).build()));
    }

    private MultipartFile excel(String[][] filas) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            String[] cabeceras = {"Numero Tarjeta", "Alias", "Proveedor"};
            for (int i = 0; i < cabeceras.length; i++) header.createCell(i).setCellValue(cabeceras[i]);
            for (int r = 0; r < filas.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int i = 0; i < filas[r].length; i++) row.createCell(i).setCellValue(filas[r][i]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "tarjetas.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    void importaTarjetaValidaConProveedorActivo() throws Exception {
        MultipartFile file = excel(new String[][]{
                {NUMERO_1, "Mi tarjeta", PROVEEDOR_ACTIVO},
        });

        ImportTarjetasAltaReportDTO report = importService.importar(file);

        assertThat(report.creados()).isEqualTo(1);
        assertThat(report.existentes()).isZero();
        assertThat(report.errores()).isEmpty();
        assertThat(tarjetaRepository.findByNumeroTarjeta(NUMERO_1)).isPresent();
        assertThat(tarjetaRepository.findByNumeroTarjeta(NUMERO_1).get().getProveedor().getCodigo())
                .isEqualTo(PROVEEDOR_ACTIVO);
    }

    @Test
    void proveedorInactivoNoCreaTarjeta() throws Exception {
        MultipartFile file = excel(new String[][]{
                {NUMERO_1, "", PROVEEDOR_INACTIVO},
        });

        ImportTarjetasAltaReportDTO report = importService.importar(file);

        assertThat(report.creados()).isZero();
        assertThat(report.errores()).hasSize(1);
        assertThat(report.errores().get(0)).contains("no registrado o inactivo");
        assertThat(tarjetaRepository.findByNumeroTarjeta(NUMERO_1)).isEmpty();
    }

    @Test
    void proveedorInexistenteNoCreaTarjetaHuerfana() throws Exception {
        MultipartFile file = excel(new String[][]{
                {NUMERO_1, "", "SECTEST_NO_EXISTE_XYZ"},
        });

        ImportTarjetasAltaReportDTO report = importService.importar(file);

        assertThat(report.creados()).isZero();
        assertThat(report.errores()).hasSize(1);
        assertThat(tarjetaRepository.findByNumeroTarjeta(NUMERO_1)).isEmpty();
    }

    @Test
    void reimportarElMismoNumeroSeCuentaComoExistente() throws Exception {
        MultipartFile primero = excel(new String[][]{{NUMERO_1, "", PROVEEDOR_ACTIVO}});
        importService.importar(primero);

        MultipartFile segundo = excel(new String[][]{{NUMERO_1, "", PROVEEDOR_ACTIVO}});
        ImportTarjetasAltaReportDTO report = importService.importar(segundo);

        assertThat(report.creados()).isZero();
        assertThat(report.existentes()).isEqualTo(1);
    }
}
