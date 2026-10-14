package com.tecozam.bills.auth.application;

import com.tecozam.bills.auth.dto.ImportUsuariosReportDTO;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
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
class UsuarioImportServiceIT {

    private static final String USER_OFICINA = "sectest_import_oficina";
    private static final String USER_CAMPO = "sectest_import_campo";

    @Autowired
    private UsuarioImportService importService;

    @Autowired
    private UsuarioOficinaRepository usuarioOficinaRepository;

    @Autowired
    private UsuarioCampoRepository usuarioCampoRepository;

    @Autowired
    private TrabajadorRepository trabajadorRepository;

    @AfterEach
    void limpiar() {
        usuarioOficinaRepository.findAll().stream()
                .filter(u -> USER_OFICINA.equals(u.getUsername()))
                .forEach(u -> {
                    usuarioOficinaRepository.delete(u);
                    if (u.getTrabajador() != null) trabajadorRepository.delete(u.getTrabajador());
                });
        usuarioCampoRepository.findAll().stream()
                .filter(u -> USER_CAMPO.equals(u.getUsername()))
                .forEach(u -> {
                    usuarioCampoRepository.delete(u);
                    if (u.getTrabajador() != null) trabajadorRepository.delete(u.getTrabajador());
                });
    }

    private MultipartFile excel(String[][] filas) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            String[] cabeceras = {"Tipo", "Username", "Password", "Nombre", "Apellidos", "Email", "Dni", "Telefono", "Rol"};
            for (int i = 0; i < cabeceras.length; i++) header.createCell(i).setCellValue(cabeceras[i]);
            for (int r = 0; r < filas.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int i = 0; i < filas[r].length; i++) row.createCell(i).setCellValue(filas[r][i]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile("file", "usuarios.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    void importaOficinaYCampoYReportaUnaFilaInvalida() throws Exception {
        MultipartFile file = excel(new String[][]{
                {"OFICINA", USER_OFICINA, "Clave123x", "Sectest Oficina", "", "", "", "", "GESTOR"},
                {"CAMPO", USER_CAMPO, "Clave123x", "Sectest", "Campo", "", "", "600111222", ""},
                {"OFICINA", "sectest_sin_rol", "Clave123x", "Sin Rol", "", "", "", "", ""},
        });

        ImportUsuariosReportDTO report = importService.importar(file);

        assertThat(report.creados()).isEqualTo(2);
        assertThat(report.existentes()).isZero();
        assertThat(report.errores()).hasSize(1);
        assertThat(report.errores().get(0)).contains("rol");
        assertThat(usuarioOficinaRepository.existsByUsername(USER_OFICINA)).isTrue();
        assertThat(usuarioCampoRepository.existsByUsername(USER_CAMPO)).isTrue();
        assertThat(usuarioOficinaRepository.existsByUsername("sectest_sin_rol")).isFalse();
    }

    @Test
    void reimportarElMismoUsernameSeCuentaComoExistente() throws Exception {
        MultipartFile primero = excel(new String[][]{
                {"OFICINA", USER_OFICINA, "Clave123x", "Sectest Oficina", "", "", "", "", "GESTOR"}});
        importService.importar(primero);

        MultipartFile segundo = excel(new String[][]{
                {"OFICINA", USER_OFICINA, "Clave123x", "Sectest Oficina", "", "", "", "", "GESTOR"}});
        ImportUsuariosReportDTO report = importService.importar(segundo);

        assertThat(report.creados()).isZero();
        assertThat(report.existentes()).isEqualTo(1);
    }
}
