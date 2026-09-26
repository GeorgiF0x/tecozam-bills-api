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
    @DisplayName("Fila Cepsa GASOLEO toma el tipo del lote (TARJETA), con sus campos")
    void filaTarjetaCombustible() {
        try (Workbook wb = XlsxFixtureBuilder.cepsaMini()) {
            Sheet sheet = wb.getSheetAt(0);
            var headers = parser.leerCabeceras(sheet);
            Optional<FilaImportada> fila = parser.parse(sheet.getRow(1), headers, TipoRecurso.TARJETA);
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
    @DisplayName("Fila Cepsa PORTAGEM toma TARJETA si el lote se declaró TARJETA (ya no se adivina VIAT por el concepto)")
    void filaPortagemNoSeAdivinaComoViatSiLoteEsTarjeta() {
        try (Workbook wb = XlsxFixtureBuilder.cepsaMini()) {
            Sheet sheet = wb.getSheetAt(0);
            var headers = parser.leerCabeceras(sheet);
            Optional<FilaImportada> fila = parser.parse(sheet.getRow(5), headers, TipoRecurso.TARJETA);
            assertThat(fila).isPresent();
            assertThat(fila.get().tipo()).isEqualTo(TipoRecurso.TARJETA);
            assertThat(fila.get().numero()).isEqualTo("7076460769900077");
            assertThat(fila.get().concepto()).isEqualTo("PORTAGEM");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("La misma fila toma VIAT si el admin declaró el lote como VIAT")
    void mismaFilaTomaViatSiLoteEsViat() {
        try (Workbook wb = XlsxFixtureBuilder.cepsaMini()) {
            Sheet sheet = wb.getSheetAt(0);
            var headers = parser.leerCabeceras(sheet);
            Optional<FilaImportada> fila = parser.parse(sheet.getRow(5), headers, TipoRecurso.VIAT);
            assertThat(fila).isPresent();
            assertThat(fila.get().tipo()).isEqualTo(TipoRecurso.VIAT);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Fila con concepto desconocido toma el tipo del lote pero se reporta (conceptoConocido=false)")
    void filaConceptoDesconocidoTomaTipoLotePeroSeReporta() {
        try (Workbook wb = XlsxFixtureBuilder.cepsaMini()) {
            Sheet sheet = wb.getSheetAt(0);
            var headers = parser.leerCabeceras(sheet);
            Optional<FilaImportada> fila = parser.parse(sheet.getRow(6), headers, TipoRecurso.TARJETA);
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
            Optional<FilaImportada> fila = parser.parse(row, headers, TipoRecurso.TARJETA);
            assertThat(fila).isEmpty();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
