package com.tecozam.bills.tarjeta.infrastructure.import_;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TarjetaXlsxRowParserTest {

    private final TarjetaXlsxRowParser parser = new TarjetaXlsxRowParser();

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
        Sheet sheet = hojaConFilas(new String[]{"Numero Tarjeta", "Alias", "Proveedor"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        assertThat(headers).containsKeys("NUMERO_TARJETA", "ALIAS", "PROVEEDOR");
    }

    @Test
    @DisplayName("Fila valida se parsea correctamente")
    void filaValida() {
        Sheet sheet = hojaConFilas(
                new String[]{"Numero Tarjeta", "Alias", "Proveedor"},
                new String[]{"1234567890", "Furgoneta 1", "REPSOL"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaTarjetaImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isTrue();
        assertThat(fila.get().numeroTarjeta()).isEqualTo("1234567890");
        assertThat(fila.get().alias()).isEqualTo("Furgoneta 1");
        assertThat(fila.get().codigoProveedor()).isEqualTo("REPSOL");
    }

    @Test
    @DisplayName("Fila sin numero de tarjeta es invalida")
    void sinNumeroTarjetaEsInvalido() {
        Sheet sheet = hojaConFilas(
                new String[]{"Numero Tarjeta", "Proveedor"},
                new String[]{"", "REPSOL"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaTarjetaImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
        assertThat(fila.get().errorValidacion()).contains("Numero Tarjeta");
    }

    @Test
    @DisplayName("Fila sin proveedor es invalida")
    void sinProveedorEsInvalido() {
        Sheet sheet = hojaConFilas(
                new String[]{"Numero Tarjeta", "Proveedor"},
                new String[]{"1234567890", ""});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaTarjetaImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isFalse();
        assertThat(fila.get().errorValidacion()).contains("Proveedor");
    }

    @Test
    @DisplayName("Alias es opcional")
    void aliasEsOpcional() {
        Sheet sheet = hojaConFilas(
                new String[]{"Numero Tarjeta", "Proveedor"},
                new String[]{"1234567890", "REPSOL"});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaTarjetaImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isPresent();
        assertThat(fila.get().esValida()).isTrue();
        assertThat(fila.get().alias()).isEmpty();
    }

    @Test
    @DisplayName("Fila completamente vacia se ignora")
    void filaVaciaSeIgnora() {
        Sheet sheet = hojaConFilas(
                new String[]{"Numero Tarjeta", "Proveedor"},
                new String[]{"", ""});
        Map<String, Integer> headers = parser.leerCabeceras(sheet);
        Optional<FilaTarjetaImportada> fila = parser.parse(sheet.getRow(1), headers);
        assertThat(fila).isEmpty();
    }
}
