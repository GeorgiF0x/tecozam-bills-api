package com.tecozam.bills.vehiculo.application;

import com.tecozam.bills.vehiculo.dto.ImportVehiculosReportDTO;
import com.tecozam.bills.vehiculo.infrastructure.persistence.VehiculoRepository;
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
class VehiculoImportServiceIT {

    private static final String MATRICULA_1 = "9988ZZZ";
    private static final String MATRICULA_2 = "9977YYY";

    @Autowired
    private VehiculoImportService importService;

    @Autowired
    private VehiculoRepository vehiculoRepository;

    @AfterEach
    void limpiar() {
        vehiculoRepository.findByMatricula(MATRICULA_1).ifPresent(vehiculoRepository::delete);
        vehiculoRepository.findByMatricula(MATRICULA_2).ifPresent(vehiculoRepository::delete);
    }

    private MultipartFile excel(String[][] filas) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            String[] cabeceras = {"Tipo", "Matricula", "CodigoObra", "Categoria", "Descripcion"};
            for (int i = 0; i < cabeceras.length; i++) header.createCell(i).setCellValue(cabeceras[i]);
            for (int r = 0; r < filas.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int i = 0; i < filas[r].length; i++) row.createCell(i).setCellValue(filas[r][i]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "vehiculos.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    void importaDosVehiculosValidosYReportaUnaFilaInvalida() throws Exception {
        MultipartFile file = excel(new String[][]{
                {"Furgoneta", MATRICULA_1, "", "VEHICULO", "Furgoneta de prueba"},
                {"Turismo", MATRICULA_2, "", "", "Turismo de prueba"},
                {"Camion", "", "", "VEHICULO", "Sin matricula, invalida"},
        });

        ImportVehiculosReportDTO report = importService.importar(file);

        assertThat(report.creados()).isEqualTo(2);
        assertThat(report.existentes()).isZero();
        assertThat(report.errores()).hasSize(1);
        assertThat(report.errores().get(0)).contains("matricula");
        assertThat(vehiculoRepository.existsByMatricula(MATRICULA_1)).isTrue();
        assertThat(vehiculoRepository.existsByMatricula(MATRICULA_2)).isTrue();
    }

    @Test
    void reimportarLaMismaMatriculaSeCuentaComoExistente() throws Exception {
        MultipartFile primero = excel(new String[][]{{"Furgoneta", MATRICULA_1, "", "VEHICULO", ""}});
        importService.importar(primero);

        MultipartFile segundo = excel(new String[][]{{"Furgoneta", MATRICULA_1, "", "VEHICULO", ""}});
        ImportVehiculosReportDTO report = importService.importar(segundo);

        assertThat(report.creados()).isZero();
        assertThat(report.existentes()).isEqualTo(1);
    }
}
