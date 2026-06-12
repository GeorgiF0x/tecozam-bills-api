package com.tecozam.bills.admin.infrastructure.import_;

import com.tecozam.bills.shared.fixtures.XlsxFixtureBuilder;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RepsolXlsxRowParserTest {

    private final RepsolXlsxRowParser parser = new RepsolXlsxRowParser();

    @Test
    @DisplayName("Lee cabeceras Repsol — incluye DES_PRODU")
    void leerCabecerasRepsol() {
        try (Workbook wb = XlsxFixtureBuilder.repsolMini()) {
            var headers = parser.leerCabeceras(wb.getSheetAt(0));
            assertThat(headers).containsKeys(
                    RepsolXlsxRowParser.KEY_NUMERO,
                    RepsolXlsxRowParser.KEY_MATRICULA,
                    RepsolXlsxRowParser.KEY_NOMBRE,
                    RepsolXlsxRowParser.KEY_CENTRO,
                    RepsolXlsxRowParser.KEY_DES_PRODU);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Fila Repsol DIESEL E+ se parsea como TARJETA con número Excel (sin normalizar)")
    void filaTarjetaCombustible() {
        try (Workbook wb = XlsxFixtureBuilder.repsolMini()) {
            Sheet sheet = wb.getSheetAt(0);
            var headers = parser.leerCabeceras(sheet);
            Optional<FilaImportada> fila = parser.parse(sheet.getRow(1), headers);
            assertThat(fila).isPresent();
            FilaImportada f = fila.get();
            assertThat(f.numero()).isEqualTo("0007078833651671188");
            assertThat(f.matricula()).isEqualTo("DUMITRU");
            assertThat(f.nombreCompleto()).isEqualTo("DUMITRU IONASCU");
            assertThat(f.centroCoste()).isEqualTo("ESPAÑA");
            assertThat(f.concepto()).isEqualTo("DIESEL E+");
            assertThat(f.tipo()).isEqualTo(TipoRecurso.TARJETA);
            assertThat(f.conceptoConocido()).isTrue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Fila Repsol AUTOPISTAS se parsea como VIAT")
    void filaViatAutopistas() {
        try (Workbook wb = XlsxFixtureBuilder.repsolMini()) {
            Sheet sheet = wb.getSheetAt(0);
            var headers = parser.leerCabeceras(sheet);
            Optional<FilaImportada> fila = parser.parse(sheet.getRow(4), headers);
            assertThat(fila).isPresent();
            assertThat(fila.get().tipo()).isEqualTo(TipoRecurso.VIAT);
            assertThat(fila.get().concepto()).isEqualTo("AUTOPISTAS");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Fila con DES_PRODU desconocido → TARJETA + conceptoConocido=false")
    void filaDesprodDesconocidoEsTarjetaPeroReportada() {
        try (Workbook wb = XlsxFixtureBuilder.repsolMini()) {
            Sheet sheet = wb.getSheetAt(0);
            var headers = parser.leerCabeceras(sheet);
            Optional<FilaImportada> fila = parser.parse(sheet.getRow(5), headers);
            assertThat(fila).isPresent();
            assertThat(fila.get().tipo()).isEqualTo(TipoRecurso.TARJETA);
            assertThat(fila.get().conceptoConocido()).isFalse();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
