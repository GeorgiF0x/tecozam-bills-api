package com.tecozam.bills.admin.application;

import com.tecozam.bills.admin.dto.ImportTarjetasReportDTO;
import com.tecozam.bills.centrocoste.domain.CentroCoste;
import com.tecozam.bills.centrocoste.infrastructure.persistence.CentroCosteRepository;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.fixtures.XlsxFixtureBuilder;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.viat.domain.Viat;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ListadoTarjetasImportServiceIT {

    @Autowired
    private ListadoTarjetasImportService importService;

    @Autowired
    private TarjetaRepository tarjetaRepo;

    @Autowired
    private ViatRepository viatRepo;

    @Autowired
    private ProveedorRepository proveedorRepo;

    @Autowired
    private CentroCosteRepository centroCosteRepo;

    @BeforeEach
    void sembrarProveedoresSiFaltan() {
        // El test no debe depender de que el proveedor ya exista en la BD
        // compartida de dev (se vio romperse de verdad: "Proveedor no
        // registrado: REPSOL" tras un reset de datos). @Transactional
        // deshace esto al final de cada test.
        java.util.List.of("REPSOL", "MOEVE_CEPSA").forEach(codigo ->
                proveedorRepo.findByCodigo(codigo).orElseGet(() ->
                        proveedorRepo.save(Proveedor.builder()
                                .codigo(codigo)
                                .nombre(codigo)
                                .activo(true)
                                .build())));
    }

    @BeforeEach
    void limpiarRegistrosDeFixtures() {
        // Tarjetas que las fixtures meten (canonical + posible variante no-canónica de smoke tests previos)
        java.util.List.of(
                "078833651671188", "078833651671246", "078833651671295",
                "078833651670503", "078833651671402", "078833651673937",
                "0007078833651671188", "0007078833651671246", "0007078833651671295",
                "0007078833651670503", "0007078833651671402", "0007078833651673937",
                "708011008022409211", "708011008022416612", "708011008022414419",
                "708011008022414013", "0972401002002270964"
        ).forEach(num -> tarjetaRepo.findByNumeroTarjeta(num).ifPresent(tarjetaRepo::delete));
        java.util.List.of(
                "0007078833651670503",
                "7076460769901026", "7076460769900077",
                "708011008022409211", "708011008022416612", "708011008022414419",
                "0972401002002270964"
        ).forEach(cod -> viatRepo.findByCodigo(cod).ifPresent(viatRepo::delete));
    }

    @Test
    @DisplayName("Cepsa (esViat=false): TODAS las filas del listado-mini se importan como tarjetas, incluidas las que antes se adivinaban como VIAT por el concepto")
    void importCepsaMiniConEsViatFalseNoCreaNingunViat() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa-mini.xlsx");

        ImportTarjetasReportDTO report = importService.importar(file, "CEPSA", false);

        assertThat(report.viatsCreados())
                .as("esViat=false: nunca se crea ningún VIAT, sin importar el texto del concepto")
                .isZero();
        assertThat(report.tarjetasCreadas() + report.tarjetasExistentes())
                .as("Todas las filas parseables (combustible + peaje + desconocido) se importan como tarjeta")
                .isEqualTo(6);
        assertThat(report.filasIgnoradas())
                .as("La fila con CONCEPTO-NO-CONOCIDO se sigue reportando (dato de calidad, no depende del tipo)")
                .isEqualTo(1);
        assertThat(report.duracionMs()).isPositive();
    }

    @Test
    @DisplayName("Cepsa (esViat=true): TODAS las filas del listado-mini se importan como VIAT")
    void importCepsaMiniConEsViatTrueCreaSoloViats() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa-mini.xlsx");

        ImportTarjetasReportDTO report = importService.importar(file, "CEPSA", true);

        assertThat(report.tarjetasCreadas())
                .as("esViat=true: nunca se crea ninguna tarjeta, sin importar el texto del concepto")
                .isZero();
        assertThat(report.viatsCreados() + report.viatsExistentes())
                .as("Todas las filas parseables se importan como VIAT")
                .isEqualTo(6);
    }

    @Test
    @DisplayName("Cepsa: las tarjetas Cepsa (7080*) NO acaban en el maestro VIAT salvo que el admin lo declare explícitamente (regresión BILLS-04 y del bug de auto-clasificación)")
    void cepsaTarjetasNoVanAlMaestroViatSalvoDeclaracionExplicita() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa-mini.xlsx");

        importService.importar(file, "CEPSA", false);

        assertThat(viatRepo.findByCodigo("708011008022409211")).isEmpty();
        assertThat(viatRepo.findByCodigo("708011008022416612")).isEmpty();
        assertThat(viatRepo.findByCodigo("708011008022414419")).isEmpty();
        assertThat(tarjetaRepo.findByNumeroTarjeta("708011008022409211")).isPresent();
        assertThat(tarjetaRepo.findByNumeroTarjeta("7076460769901026"))
                .as("Con esViat=false, incluso la fila de peaje real se importa como tarjeta")
                .isPresent();
    }

    @Test
    @DisplayName("Repsol: número se persiste en forma canónica (15 dígitos sin prefijo 0007)")
    void repsolPersisteNumeroCanonico() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.repsolMini(), "repsol-mini.xlsx");

        importService.importar(file, "REPSOL", false);

        assertThat(tarjetaRepo.findByNumeroTarjeta("078833651671188"))
                .as("Excel guardaba 0007078833651671188; canónico = 15 dígitos sin prefijo")
                .isPresent();
        assertThat(tarjetaRepo.findByNumeroTarjeta("0007078833651671188"))
                .as("La forma no canónica NO se persiste")
                .isEmpty();
    }

    @Test
    @DisplayName("Idempotencia: segundo import del mismo Excel no duplica ningún registro")
    void segundoImportDelMismoExcelNoDuplica() throws Exception {
        importService.importar(toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa.xlsx"), "CEPSA", false);
        long tarjetasTrasPrimero = tarjetaRepo.count();
        long viatsTrasPrimero = viatRepo.count();

        ImportTarjetasReportDTO segundo = importService.importar(
                toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa.xlsx"), "CEPSA", false);

        assertThat(segundo.tarjetasCreadas()).isZero();
        assertThat(segundo.viatsCreados()).isZero();
        assertThat(segundo.tarjetasExistentes()).isPositive();
        assertThat(tarjetaRepo.count()).isEqualTo(tarjetasTrasPrimero);
        assertThat(viatRepo.count()).isEqualTo(viatsTrasPrimero);
    }

    @Test
    @DisplayName("Repsol: concepto vacío y número SIN prefijo 0007 se reclasifica automáticamente a VIAT")
    void repsolConceptoVacioYNumeroSinPrefijoSeReclasificaAViat() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.repsolConViatPorNumero(), "repsol-viat.xlsx");

        ImportTarjetasReportDTO report = importService.importar(file, "REPSOL", false);

        assertThat(report.reclasificadosPorNumero())
                .as("Solo la fila sin prefijo 0007 se reclasifica")
                .isEqualTo(1);
        assertThat(report.tarjetasCreadas())
                .as("La fila con prefijo 0007 sigue como tarjeta normal")
                .isEqualTo(1);
        assertThat(report.viatsCreados())
                .as("La fila sin prefijo 0007 se crea como VIAT, no como tarjeta")
                .isEqualTo(1);
        assertThat(tarjetaRepo.findByNumeroTarjeta("078833651673937")).isPresent();
        assertThat(tarjetaRepo.findByNumeroTarjeta("0972401002002270964")).isEmpty();
        assertThat(viatRepo.findByCodigo("0972401002002270964")).isPresent();
        assertThat(report.errores())
                .anyMatch(e -> e.contains("0972401002002270964")
                        && e.contains("reclasificado automáticamente como VIAT")
                        && e.contains("número fuera del prefijo Repsol conocido (0007)"));
    }

    @Test
    @DisplayName("Repsol: concepto vacío pero número SÍ tiene prefijo 0007 → no se reclasifica, mensaje solo se enriquece con la serie")
    void repsolConceptoVacioYNumeroConPrefijoNoSeReclasifica() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.repsolMini(), "repsol-mini.xlsx");

        ImportTarjetasReportDTO report = importService.importar(file, "REPSOL", false);

        assertThat(report.reclasificadosPorNumero())
                .as("Ninguna fila de repsolMini carece del prefijo 0007")
                .isZero();
        assertThat(report.viatsCreados()).isZero();
        assertThat(report.filasIgnoradas()).isEqualTo(1);
        assertThat(report.errores())
                .as("Fila CONCEPTO-NO-CONOCIDO mantiene el motivo y suma la serie, sin mensaje de reclasificación")
                .anyMatch(e -> e.contains("CONCEPTO-NO-CONOCIDO") && e.contains("serie 0007078833")
                        && !e.contains("reclasificado"));
    }

    @Test
    @DisplayName("Cepsa: la fila con concepto desconocido enriquece el mensaje con la serie del número, sin reclasificar (sin señal fiable)")
    void cepsaConceptoDesconocidoEnriqueceMensajeConSerieSinReclasificar() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa-mini.xlsx");

        ImportTarjetasReportDTO report = importService.importar(file, "CEPSA", false);

        assertThat(report.reclasificadosPorNumero())
                .as("Cepsa/Moeve nunca se reclasifica por número (sin señal fiable)")
                .isZero();
        assertThat(report.errores())
                .anyMatch(e -> e.contains("708011008022414013") && e.contains("serie 7080110080"));
    }

    @Test
    @DisplayName("Repsol: tarjeta existente pero soft-deleted se restaura (eliminado_en vuelve a null) al reimportar, no solo se cuenta como existente")
    void repsolTarjetaSoftDeletedSeRestauraAlReimportar() throws Exception {
        Proveedor repsol = proveedorRepo.findByCodigo("REPSOL").orElseThrow();
        Tarjeta softDeleted = Tarjeta.builder()
                .numeroTarjeta("078833651671188")
                .alias("DUMITRU")
                .proveedor(repsol)
                .estado(EstadoRecurso.DISPONIBLE)
                .activa(true)
                .build();
        softDeleted.softDelete();
        tarjetaRepo.save(softDeleted);
        assertThat(tarjetaRepo.findByNumeroTarjeta("078833651671188").orElseThrow().isEliminado()).isTrue();

        MultipartFile file = toMultipart(XlsxFixtureBuilder.repsolMini(), "repsol-mini.xlsx");
        ImportTarjetasReportDTO report = importService.importar(file, "REPSOL", false);

        assertThat(report.tarjetasExistentes())
                .as("Se sigue contando como existente, igual que antes")
                .isPositive();
        Tarjeta restaurada = tarjetaRepo.findByNumeroTarjeta("078833651671188").orElseThrow();
        assertThat(restaurada.isEliminado())
                .as("odd/tasks/clasificacion-viat-por-numero.md punto 8/9: debe restaurarse, no quedar invisible para siempre")
                .isFalse();
        assertThat(tarjetaRepo.findByEliminadoEnIsNull())
                .as("Visible de verdad en el listado real, no solo contada en el reporte del modal")
                .anyMatch(t -> t.getNumeroTarjeta().equals("078833651671188"));
    }

    @Test
    @DisplayName("Cepsa (esViat=true): VIAT existente pero soft-deleted se restaura al reimportar")
    void cepsaViatSoftDeletedSeRestauraAlReimportar() throws Exception {
        Viat softDeleted = Viat.builder()
                .codigo("708011008022409211")
                .numeroSerie("0034MFB")
                .descripcion("DAVID CASTUERA")
                .estado(EstadoRecurso.DISPONIBLE)
                .activo(true)
                .build();
        softDeleted.softDelete();
        viatRepo.save(softDeleted);
        assertThat(viatRepo.findByCodigo("708011008022409211").orElseThrow().isEliminado()).isTrue();

        MultipartFile file = toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa-mini.xlsx");
        ImportTarjetasReportDTO report = importService.importar(file, "CEPSA", true);

        assertThat(report.viatsExistentes())
                .as("Se sigue contando como existente, igual que antes")
                .isPositive();
        Viat restaurado = viatRepo.findByCodigo("708011008022409211").orElseThrow();
        assertThat(restaurado.isEliminado())
                .as("odd/tasks/clasificacion-viat-por-numero.md punto 8/9: debe restaurarse, no quedar invisible para siempre")
                .isFalse();
        assertThat(viatRepo.findByEliminadoEnIsNull())
                .as("Visible de verdad en el listado real, no solo contado en el reporte del modal")
                .anyMatch(v -> v.getCodigo().equals("708011008022409211"));
    }

    @Test
    @DisplayName("Repsol: centro de coste existente pero soft-deleted se restaura al reimportar")
    void centroCosteSoftDeletedSeRestauraAlReimportar() throws Exception {
        CentroCoste softDeleted = CentroCoste.builder()
                .codigo("CC-SOFT-DELETE-TEST")
                .nombre("CC-SOFT-DELETE-TEST")
                .activo(true)
                .build();
        softDeleted.softDelete();
        centroCosteRepo.save(softDeleted);
        assertThat(centroCosteRepo.findByCodigo("CC-SOFT-DELETE-TEST").orElseThrow().isEliminado()).isTrue();

        MultipartFile file = toMultipart(XlsxFixtureBuilder.repsolConCentroCosteSoftDeleted(), "repsol-cc.xlsx");
        importService.importar(file, "REPSOL", false);

        CentroCoste restaurado = centroCosteRepo.findByCodigo("CC-SOFT-DELETE-TEST").orElseThrow();
        assertThat(restaurado.isEliminado())
                .as("odd/tasks/clasificacion-viat-por-numero.md punto 8/9: debe restaurarse, no quedar invisible para siempre")
                .isFalse();
        assertThat(centroCosteRepo.findByEliminadoEnIsNull())
                .as("Visible de verdad en el listado real, no solo contado en el reporte del modal")
                .anyMatch(c -> c.getCodigo().equals("CC-SOFT-DELETE-TEST"));
    }

    @Test
    @DisplayName("Proveedor desconocido lanza excepción amigable (no 500)")
    void proveedorDesconocidoLanzaIllegalArg() throws Exception {
        MultipartFile file = toMultipart(XlsxFixtureBuilder.cepsaMini(), "cepsa.xlsx");
        assertThatThrownBy(() -> importService.importar(file, "GALP", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GALP");
    }

    private static MultipartFile toMultipart(Workbook wb, String filename) throws Exception {
        try (wb; ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            wb.write(baos);
            return new MockMultipartFile(
                    "file", filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baos.toByteArray());
        }
    }
}
