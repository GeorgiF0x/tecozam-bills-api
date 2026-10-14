package com.tecozam.bills.trabajador.infrastructure.import_;

import com.tecozam.bills.trabajador.domain.OrigenTrabajador;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TrabajadorXlsxRowParserTest {

    private final TrabajadorXlsxRowParser parser = new TrabajadorXlsxRowParser();

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

    @Test
    @DisplayName("Lee cabeceras insensibles a tildes/mayusculas")
    void leerCabeceras() {
        Sheet sheet = hojaConFilas(new String[]{"Nombre", "Apellidos", "Email", "DNI/NIE", "Origen"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        assertThat(headers).containsKeys("NOMBRE", "APELLIDOS", "EMAIL", "DNI_NIE", "ORIGEN");
    }

    @Test
    @DisplayName("Fila valida con todos los campos se parsea correctamente")
    void filaValidaCompleta() {
        Sheet sheet = hojaConFilas(
                new String[]{"Nombre", "Apellidos", "Email", "DNI/NIE", "Origen"},
                new String[]{"Juan", "Garcia Lopez", "juan@test.com", "12345678A", "CAMPO"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaTrabajadorImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isTrue();
        assertThat(fila.get().nombre()).isEqualTo("Juan");
        assertThat(fila.get().apellidos()).isEqualTo("Garcia Lopez");
        assertThat(fila.get().origen()).isEqualTo(OrigenTrabajador.CAMPO);
    }

    @Test
    @DisplayName("Origen vacio por defecto es IMPORTACION")
    void origenPorDefecto() {
        Sheet sheet = hojaConFilas(
                new String[]{"Nombre", "Apellidos"},
                new String[]{"Ana", "Perez"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaTrabajadorImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().origen()).isEqualTo(OrigenTrabajador.IMPORTACION);
    }

    @Test
    @DisplayName("Origen no reconocido cae a IMPORTACION por defecto en silencio")
    void origenInvalidoCaeADefecto() {
        Sheet sheet = hojaConFilas(
                new String[]{"Nombre", "Apellidos", "Origen"},
                new String[]{"Luis", "Martin", "REMOTO"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaTrabajadorImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isTrue();
        assertThat(fila.get().origen()).isEqualTo(OrigenTrabajador.IMPORTACION);
    }

    @Test
    @DisplayName("Fila sin nombre es invalida")
    void sinNombreEsInvalido() {
        Sheet sheet = hojaConFilas(
                new String[]{"Nombre", "Apellidos"},
                new String[]{"", "Perez"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaTrabajadorImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
        assertThat(fila.get().errorValidacion()).contains("obligatorios");
    }

    @Test
    @DisplayName("Fila sin apellidos es invalida")
    void sinApellidosEsInvalido() {
        Sheet sheet = hojaConFilas(
                new String[]{"Nombre", "Apellidos"},
                new String[]{"Ana", ""});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaTrabajadorImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
    }

    @Test
    @DisplayName("Fila completamente vacia se ignora")
    void filaVaciaSeIgnora() {
        Sheet sheet = hojaConFilas(
                new String[]{"Nombre", "Apellidos"},
                new String[]{"", ""});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaTrabajadorImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isEmpty();
    }
}
