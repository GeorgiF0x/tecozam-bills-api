package com.tecozam.bills.admin.infrastructure.import_;

import org.apache.poi.ss.usermodel.Sheet;

/**
 * Strategy para procesar la hoja del listado de tarjetas de un proveedor concreto.
 *
 * <p>El orquestador {@code ListadoTarjetasImportService} resuelve la
 * implementación adecuada vía {@code ListadoTarjetasImporterFactory},
 * carga el workbook y delega aquí. Cada implementación conoce las cabeceras
 * particulares de su proveedor ({@code DES_PRODU} en Repsol, {@code CONCEPTOS}
 * en Cepsa) y decide tarjeta vs VIAT mediante {@link ConceptoClassifier}.
 *
 * <p>El contrato es void y mutable: la implementación actualiza los contadores
 * y caches de {@code ctx} en vez de devolver un report parcial.
 */
public interface ListadoTarjetasImporter {

    /**
     * Procesa la hoja de listado de tarjetas. Itera fila a fila, decide
     * tarjeta o VIAT, crea o reutiliza trabajadores y centros de coste, y
     * actualiza los contadores del {@code ctx}.
     */
    void procesar(Sheet hoja, ImportContext ctx);
}
