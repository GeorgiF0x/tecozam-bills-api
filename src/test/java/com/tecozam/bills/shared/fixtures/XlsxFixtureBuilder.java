package com.tecozam.bills.shared.fixtures;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Helper para crear workbooks Excel in-memory con datos representativos del
 * listado de tarjetas de cada proveedor.
 *
 * <p>Evita versionar binarios {@code .xlsx} en git: cada test crea su workbook
 * al vuelo, con datos auditables en el propio código.
 *
 * <p>Las filas reproducen ejemplos reales del listado del cliente (Excels en
 * la raíz del repo, fuera de git) recortados a una muestra mínima representativa
 * para cubrir los escenarios del spec.
 */
public final class XlsxFixtureBuilder {

    private XlsxFixtureBuilder() {
        // Test utility — no instances.
    }

    /**
     * Workbook con cabeceras de Cepsa/Moeve y 6 filas representativas:
     * <ul>
     *   <li>3 tarjetas con concepto combustible (GASOLEO, DIESEL STAR, ECOBLUE)</li>
     *   <li>2 VIATs con concepto de peaje (AUTOPISTAS DE PEAJE, PORTAGEM)</li>
     *   <li>1 fila con concepto desconocido (cae a TARJETA + filasIgnoradas)</li>
     * </ul>
     */
    public static Workbook cepsaMini() {
        Workbook wb = new XSSFWorkbook();
        var sheet = wb.createSheet("Hoja1");
        writeRow(sheet.createRow(0),
                "NÚMERO DE TARJETA", "MATRICULA", "NOMBRE", "MATRICULA2",
                "CENTRO COSTE", "Columna1", "CONCEPTOS");
        // 3 tarjetas
        writeRow(sheet.createRow(1),
                "708011008022409211", "0034MFB", "DAVID CASTUERA",
                "", "SECOZAM", "", "GASOLEO");
        writeRow(sheet.createRow(2),
                "708011008022416612", "5307LVP", "ALBERTO GARCIA CONDE",
                "", "ESPAÑA", "", "DIESEL STAR");
        writeRow(sheet.createRow(3),
                "708011008022414419", "8094LNV", "JOSE ESTEVEZ HERNANDEZ",
                "", "ECOBLUE", "", "ECOBLUE");
        // 2 VIATs
        writeRow(sheet.createRow(4),
                "7076460769901026", "SIN MATRICULA", "ISIDORO IGLESIAS FRANCO",
                "", "ESPAÑA", "", "AUTOPISTAS DE PEAJE");
        writeRow(sheet.createRow(5),
                "7076460769900077", "7274MBB", "CARLOS RODRIGUEZ SANCHEZ",
                "", "ESPAÑA", "", "PORTAGEM");
        // 1 fila con concepto desconocido (queda como TARJETA + filasIgnoradas)
        writeRow(sheet.createRow(6),
                "708011008022414013", "1234XYZ", "PEPE PRUEBA DESCONOCIDO",
                "", "ESPAÑA", "", "CONCEPTO-NO-CONOCIDO");
        return wb;
    }

    /**
     * Workbook con cabeceras de Repsol y 5 filas representativas:
     * <ul>
     *   <li>3 tarjetas con prefijo {@code 0007} y conceptos combustible/staff</li>
     *   <li>1 VIAT con concepto AUTOPISTAS</li>
     *   <li>1 fila con concepto desconocido</li>
     * </ul>
     */
    public static Workbook repsolMini() {
        Workbook wb = new XSSFWorkbook();
        var sheet = wb.createSheet("Hoja1");
        writeRow(sheet.createRow(0),
                "NUMERO TARJETA", "MATRICULA", "NOMBRE", "CENTRO COSTE", "DES_PRODU");
        writeRow(sheet.createRow(1),
                "0007078833651671188", "DUMITRU", "DUMITRU IONASCU",
                "ESPAÑA", "DIESEL E+");
        writeRow(sheet.createRow(2),
                "0007078833651671246", "SEGIS", "SEGISMUNDO RODRIGUEZ RODRIGUES",
                "ESPAÑA", "ADBLUE");
        writeRow(sheet.createRow(3),
                "0007078833651671295", "L.CALVO", "LUIS EDUARDO CALVO RIBERA",
                "ESPAÑA", "STAFF");
        writeRow(sheet.createRow(4),
                "0007078833651670503", "8197-CYB", "ANTONIO FERNANDO RIBEIRO DE ALMEIDA",
                "ESPAÑA", "AUTOPISTAS");
        writeRow(sheet.createRow(5),
                "0007078833651671402", "A.TAMERON", "AMABLE TAMERON LOPEZ",
                "ESPAÑA", "CONCEPTO-NO-CONOCIDO");
        return wb;
    }

    private static void writeRow(Row row, String... values) {
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }
}
