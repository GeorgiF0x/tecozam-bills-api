package com.tecozam.bills.viat.application;

import com.tecozam.bills.viat.dto.ImportViatsReportDTO;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ViatImportServiceIT {

    private static final String CODIGO_1 = "VIATTEST-9988";
    private static final String CODIGO_2 = "VIATTEST-9977";

    @Autowired
    private ViatImportService importService;

    @Autowired
    private ViatRepository viatRepository;

    @AfterEach
    void limpiar() {
        viatRepository.findByCodigo(CODIGO_1).ifPresent(viatRepository::delete);
        viatRepository.findByCodigo(CODIGO_2).ifPresent(viatRepository::delete);
    }

    private MultipartFile excel(String[][] filas) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            String[] cabeceras = {"Codigo", "Numero Serie", "Descripcion"};
            for (int i = 0; i < cabeceras.length; i++) header.createCell(i).setCellValue(cabeceras[i]);
            for (int r = 0; r < filas.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int i = 0; i < filas[r].length; i++) row.createCell(i).setCellValue(filas[r][i]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "viats.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    void importaDosViatsValidosYReportaUnaFilaInvalida() throws Exception {
        MultipartFile file = excel(new String[][]{
                {CODIGO_1, "SN111", "Dispositivo de prueba 1"},
                {CODIGO_2, "SN222", "Dispositivo de prueba 2"},
                {"", "SN333", "Sin codigo, invalida"},
        });

        ImportViatsReportDTO report = importService.importar(file);

        assertThat(report.creados()).isEqualTo(2);
        assertThat(report.existentes()).isZero();
        assertThat(report.errores()).hasSize(1);
        assertThat(report.errores().get(0)).contains("codigo");
        assertThat(viatRepository.existsByCodigo(CODIGO_1)).isTrue();
        assertThat(viatRepository.existsByCodigo(CODIGO_2)).isTrue();
    }

    @Test
    void reimportarElMismoCodigoSeCuentaComoExistente() throws Exception {
        MultipartFile primero = excel(new String[][]{{CODIGO_1, "SN111", ""}});
        importService.importar(primero);

        MultipartFile segundo = excel(new String[][]{{CODIGO_1, "SN111", ""}});
        ImportViatsReportDTO report = importService.importar(segundo);

        assertThat(report.creados()).isZero();
        assertThat(report.existentes()).isEqualTo(1);
    }
}
