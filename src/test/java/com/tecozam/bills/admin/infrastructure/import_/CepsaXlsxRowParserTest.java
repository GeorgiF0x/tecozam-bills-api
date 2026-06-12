package com.tecozam.bills.admin.infrastructure.import_;

import com.tecozam.bills.shared.fixtures.XlsxFixtureBuilder;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CepsaXlsxRowParserTest {

    private final CepsaXlsxRowParser parser = new CepsaXlsxRowParser();

    @Test
    @DisplayName("Lee cabeceras y devuelve mapa columna→índice (insensible a tildes)")
    void leerCabecerasCepsa() {
        try (Workbook wb = XlsxFixtureBuilder.cepsaMini()) {
            Sheet sheet = wb.getSheetAt(0);
            var headers = parser.leerCabeceras(sheet);
            assertThat(headers).containsKey("NUMERO_TARJETA");
            assertThat(headers).containsKey("MATRICULA");
            assertThat(headers).containsKey("NOMBRE");
            assertThat(headers).containsKey("CENTRO_COSTE");
            assertThat(headers).containsKey("CONCEPTOS");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Fila Cepsa GASOLEO se parsea como TARJETA con sus campos")
    void filaTarjetaCombustible() {
        try (Workbook wb = XlsxFixtureBuilder.cepsaMini()) {
            Sheet sheet = wb.getSheetAt(0);
            var headers = parser.leerCabeceras(sheet);
            Optional<FilaImportada> fila = parser.parse(sheet.getRow(1), headers);
            assertThat(fila).isPresent();
            FilaImportada f = fila.get();
            assertThat(f.numero()).isEqualTo("708011008022409211");
            assertThat(f.matricula()).isEqualTo("0034MFB");
            assertThat(f.nombreCompleto()).isEqualTo("DAVID CASTUERA");
            assertThat(f.centroCoste()).isEqualTo("SECOZAM");
            assertThat(f.concepto()).isEqualTo("GASOLEO");
            assertThat(f.tipo()).isEqualTo(TipoRecurso.TARJETA);
            assertThat(f.conceptoConocido()).isTrue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Fila Cepsa PORTAGEM se parsea como VIAT")
    void filaViatPortagem() {
        try (Workbook wb = XlsxFixtureBuilder.cepsaMini()) {
            Sheet sheet = wb.getSheetAt(0);
            var headers = parser.leerCabeceras(sheet);
            Optional<FilaImportada> fila = parser.parse(sheet.getRow(5), headers);
            assertThat(fila).isPresent();
            assertThat(fila.get().tipo()).isEqualTo(TipoRecurso.VIAT);
            assertThat(fila.get().numero()).isEqualTo("7076460769900077");
            assertThat(fila.get().concepto()).isEqualTo("PORTAGEM");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Fila con concepto desconocido se parsea como TARJETA con conceptoConocido=false")
    void filaConceptoDesconocidoEsTarjetaPeroReportada() {
        try (Workbook wb = XlsxFixtureBuilder.cepsaMini()) {
            Sheet sheet = wb.getSheetAt(0);
            var headers = parser.leerCabeceras(sheet);
            Optional<FilaImportada> fila = parser.parse(sheet.getRow(6), headers);
            assertThat(fila).isPresent();
            assertThat(fila.get().tipo()).isEqualTo(TipoRecurso.TARJETA);
            assertThat(fila.get().conceptoConocido()).isFalse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Fila sin número de tarjeta devuelve Optional.empty")
    void filaSinNumeroSeIgnora() {
        try (Workbook wb = XlsxFixtureBuilder.cepsaMini()) {
            Sheet sheet = wb.getSheetAt(0);
            var headers = parser.leerCabeceras(sheet);
            // Añadir fila vacía
            var row = sheet.createRow(99);
            row.createCell(0).setCellValue("");
            Optional<FilaImportada> fila = parser.parse(row, headers);
            assertThat(fila).isEmpty();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
