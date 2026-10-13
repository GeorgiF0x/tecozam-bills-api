package com.tecozam.bills.vehiculo.infrastructure.import_;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VehiculoXlsxRowParserTest {

    private final VehiculoXlsxRowParser parser = new VehiculoXlsxRowParser();

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
        Sheet sheet = hojaConFilas(new String[]{"Tipo", "Matrícula", "Código Obra", "Categoría", "Descripción"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        assertThat(headers).containsKeys("TIPO", "MATRICULA", "CODIGO_OBRA", "CATEGORIA", "DESCRIPCION");
    }

    @Test
    @DisplayName("Fila valida categoria VEHICULO con matricula se parsea correctamente")
    void filaValidaVehiculo() {
        Sheet sheet = hojaConFilas(
                new String[]{"Tipo", "Matricula", "CodigoObra", "Categoria", "Descripcion"},
                new String[]{"Furgoneta", "1234ABC", "", "VEHICULO", "Furgoneta de reparto"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaVehiculoImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isTrue();
        assertThat(fila.get().matricula()).isEqualTo("1234ABC");
        assertThat(fila.get().tipo()).isEqualTo("Furgoneta");
    }

    @Test
    @DisplayName("Categoria por defecto es VEHICULO cuando la columna esta vacia")
    void categoriaPorDefecto() {
        Sheet sheet = hojaConFilas(
                new String[]{"Tipo", "Matricula"},
                new String[]{"Turismo", "9999XYZ"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaVehiculoImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().categoria()).isEqualTo("VEHICULO");
        assertThat(fila.get().esValida()).isTrue();
    }

    @Test
    @DisplayName("Fila VEHICULO sin matricula es invalida")
    void vehiculoSinMatriculaEsInvalido() {
        Sheet sheet = hojaConFilas(
                new String[]{"Tipo", "Matricula", "Categoria"},
                new String[]{"Turismo", "", "VEHICULO"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaVehiculoImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
        assertThat(fila.get().errorValidacion()).contains("matricula");
    }

    @Test
    @DisplayName("Fila INDUSTRIAL_MAQUINARIA sin codigo de obra es invalida")
    void industrialSinCodigoObraEsInvalido() {
        Sheet sheet = hojaConFilas(
                new String[]{"Tipo", "CodigoObra", "Categoria"},
                new String[]{"Maquinaria", "", "INDUSTRIAL_MAQUINARIA"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaVehiculoImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
        assertThat(fila.get().errorValidacion()).contains("codigo de obra");
    }

    @Test
    @DisplayName("Fila sin Tipo es invalida")
    void sinTipoEsInvalido() {
        Sheet sheet = hojaConFilas(
                new String[]{"Tipo", "Matricula"},
                new String[]{"", "1111AAA"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaVehiculoImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
    }

    @Test
    @DisplayName("Fila completamente vacia se ignora (no es ni valida ni invalida)")
    void filaVaciaSeIgnora() {
        Sheet sheet = hojaConFilas(
                new String[]{"Tipo", "Matricula"},
                new String[]{"", ""});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaVehiculoImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isEmpty();
    }

    @Test
    @DisplayName("Categoria invalida se reporta como fila invalida")
    void categoriaInvalida() {
        Sheet sheet = hojaConFilas(
                new String[]{"Tipo", "Matricula", "Categoria"},
                new String[]{"Turismo", "2222BBB", "AVION"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaVehiculoImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
        assertThat(fila.get().errorValidacion()).contains("Categoria");
    }
}
