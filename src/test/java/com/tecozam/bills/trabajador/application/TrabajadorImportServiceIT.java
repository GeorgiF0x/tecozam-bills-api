package com.tecozam.bills.trabajador.application;

import com.tecozam.bills.trabajador.dto.ImportTrabajadoresReportDTO;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
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
class TrabajadorImportServiceIT {

    private static final String EMAIL_1 = "sectest.import.trab1@tecozam-test.local";
    private static final String NOMBRE_2 = "SecTestImportTrab";
    private static final String APELLIDOS_2 = "Apellido2";

    @Autowired
    private TrabajadorImportService importService;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @AfterEach
    void limpiar() {
        trabajadorRepository.findByEmail(EMAIL_1).ifPresent(trabajadorRepository::delete);
        trabajadorRepository.findFirstByNombreIgnoreCaseAndApellidosIgnoreCase(NOMBRE_2, APELLIDOS_2)
                .ifPresent(trabajadorRepository::delete);
    }

    private MultipartFile excel(String[][] filas) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            String[] cabeceras = {"Nombre", "Apellidos", "Email", "DNI/NIE", "Origen"};
            for (int i = 0; i < cabeceras.length; i++) header.createCell(i).setCellValue(cabeceras[i]);
            for (int r = 0; r < filas.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int i = 0; i < filas[r].length; i++) row.createCell(i).setCellValue(filas[r][i]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "trabajadores.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    void importaDosTrabajadoresValidosYReportaUnaFilaInvalida() throws Exception {
        MultipartFile file = excel(new String[][]{
                {"SecTestImportTrab", "Apellido1", EMAIL_1, "", "CAMPO"},
                {NOMBRE_2, APELLIDOS_2, "", "", ""},
                {"", "SinNombre", "", "", ""},
        });

        ImportTrabajadoresReportDTO report = importService.importar(file);

        assertThat(report.creados()).isEqualTo(2);
        assertThat(report.existentes()).isZero();
        assertThat(report.errores()).hasSize(1);
        assertThat(report.errores().get(0)).contains("obligatorios");
        assertThat(trabajadorRepository.findByEmail(EMAIL_1)).isPresent();
        assertThat(trabajadorRepository.findByEmail(EMAIL_1).get().getOrigen().name()).isEqualTo("CAMPO");
        assertThat(trabajadorRepository
                .findFirstByNombreIgnoreCaseAndApellidosIgnoreCase(NOMBRE_2, APELLIDOS_2)).isPresent();
    }

    @Test
    void reimportarElMismoTrabajadorSeCuentaComoExistente() throws Exception {
        MultipartFile primero = excel(new String[][]{{"SecTestImportTrab", "Apellido1", EMAIL_1, "", ""}});
        importService.importar(primero);

        MultipartFile segundo = excel(new String[][]{{"SecTestImportTrab", "Apellido1", EMAIL_1, "", ""}});
        ImportTrabajadoresReportDTO report = importService.importar(segundo);

        assertThat(report.creados()).isZero();
        assertThat(report.existentes()).isEqualTo(1);
    }
}
