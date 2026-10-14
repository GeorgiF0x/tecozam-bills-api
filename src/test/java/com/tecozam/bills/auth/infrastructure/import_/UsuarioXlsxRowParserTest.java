package com.tecozam.bills.auth.infrastructure.import_;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioXlsxRowParserTest {

    private final UsuarioXlsxRowParser parser = new UsuarioXlsxRowParser();

    private Sheet hojaConFilas(String[] cabeceras, String[]... filas) {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet();
        Row header = sheet.createRow(0);
        for (int i = 0; i < cabeceras.length; i++) header.createCell(i).setCellValue(cabeceras[i]);
        for (int r = 0; r < filas.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int i = 0; i < filas[r].length; i++) row.createCell(i).setCellValue(filas[r][i]);
        }
        return sheet;
    }

    private static final String[] CABECERAS =
            {"Tipo", "Username", "Password", "Nombre", "Apellidos", "Email", "Dni", "Telefono", "Rol"};

    @Test
    @DisplayName("Lee cabeceras insensibles a tildes/mayusculas")
    void leerCabeceras() {
        Sheet sheet = hojaConFilas(CABECERAS);
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        assertThat(headers).containsKeys(
                "TIPO", "USERNAME", "PASSWORD", "NOMBRE", "APELLIDOS", "EMAIL", "DNI", "TELEFONO", "ROL");
    }

    @Test
    @DisplayName("Fila OFICINA valida se parsea correctamente")
    void filaOficinaValida() {
        Sheet sheet = hojaConFilas(CABECERAS,
                new String[]{"OFICINA", "jgarcia", "Clave123x", "Juan Garcia", "", "jg@x.com", "", "", "GESTOR"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaUsuarioImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isTrue();
        assertThat(fila.get().tipo()).isEqualTo("OFICINA");
        assertThat(fila.get().rol()).isEqualTo("GESTOR");
    }

    @Test
    @DisplayName("Fila CAMPO valida se parsea correctamente, sin exigir rol")
    void filaCampoValida() {
        Sheet sheet = hojaConFilas(CABECERAS,
                new String[]{"CAMPO", "pperez", "Clave123x", "Pedro", "Perez", "", "", "600111222", ""});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaUsuarioImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isTrue();
        assertThat(fila.get().apellidos()).isEqualTo("Perez");
        assertThat(fila.get().telefono()).isEqualTo("600111222");
    }

    @Test
    @DisplayName("Fila OFICINA sin rol es invalida")
    void oficinaSinRolEsInvalida() {
        Sheet sheet = hojaConFilas(CABECERAS,
                new String[]{"OFICINA", "jgarcia", "Clave123x", "Juan Garcia", "", "", "", "", ""});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaUsuarioImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
        assertThat(fila.get().errorValidacion()).contains("rol");
    }

    @Test
    @DisplayName("Password sin mayuscula/minuscula/digito es invalida")
    void passwordDebil() {
        Sheet sheet = hojaConFilas(CABECERAS,
                new String[]{"CAMPO", "pperez", "clave", "Pedro", "Perez", "", "", "", ""});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaUsuarioImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
        assertThat(fila.get().errorValidacion()).contains("contraseña");
    }

    @Test
    @DisplayName("Tipo invalido (ni OFICINA ni CAMPO) es invalida")
    void tipoInvalido() {
        Sheet sheet = hojaConFilas(CABECERAS,
                new String[]{"BACKOFFICE", "jgarcia", "Clave123x", "Juan", "", "", "", "", ""});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaUsuarioImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
        assertThat(fila.get().errorValidacion()).contains("Tipo");
    }

    @Test
    @DisplayName("Fila sin username es invalida")
    void sinUsernameEsInvalida() {
        Sheet sheet = hojaConFilas(CABECERAS,
                new String[]{"CAMPO", "", "Clave123x", "Pedro", "", "", "", "", ""});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaUsuarioImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
        assertThat(fila.get().errorValidacion()).contains("username");
    }

    @Test
    @DisplayName("Fila completamente vacia se ignora")
    void filaVaciaSeIgnora() {
        Sheet sheet = hojaConFilas(CABECERAS,
                new String[]{"", "", "", "", "", "", "", "", ""});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaUsuarioImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isEmpty();
    }
}
