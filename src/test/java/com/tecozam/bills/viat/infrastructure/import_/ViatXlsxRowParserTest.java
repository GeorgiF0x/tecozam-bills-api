package com.tecozam.bills.viat.infrastructure.import_;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ViatXlsxRowParserTest {

    private final ViatXlsxRowParser parser = new ViatXlsxRowParser();

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
        Sheet sheet = hojaConFilas(new String[]{"Codigo", "Número Serie", "Descripción"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        assertThat(headers).containsKeys("CODIGO", "NUMERO_SERIE", "DESCRIPCION");
    }

    @Test
    @DisplayName("Fila valida con codigo y numero de serie se parsea correctamente")
    void filaValida() {
        Sheet sheet = hojaConFilas(
                new String[]{"Codigo", "Numero Serie", "Descripcion"},
                new String[]{"VIAT-001", "SN12345", "Dispositivo de peaje"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaViatImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isTrue();
        assertThat(fila.get().codigo()).isEqualTo("VIAT-001");
        assertThat(fila.get().numeroSerie()).isEqualTo("SN12345");
    }

    @Test
    @DisplayName("Fila sin codigo es invalida")
    void sinCodigoEsInvalido() {
        Sheet sheet = hojaConFilas(
                new String[]{"Codigo", "Numero Serie"},
                new String[]{"", "SN999"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaViatImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
        assertThat(fila.get().errorValidacion()).contains("codigo");
    }

    @Test
    @DisplayName("Fila completamente vacia se ignora")
    void filaVaciaSeIgnora() {
        Sheet sheet = hojaConFilas(
                new String[]{"Codigo", "NumeroSerie"},
                new String[]{"", ""});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaViatImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isEmpty();
    }

    @Test
    @DisplayName("Fila con solo descripcion (sin codigo) se reporta invalida, no se ignora")
    void filaConDescripcionSinCodigoEsInvalida() {
        Sheet sheet = hojaConFilas(
                new String[]{"Codigo", "Descripcion"},
                new String[]{"", "Dispositivo sin codigo asignado"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaViatImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
    }
}
