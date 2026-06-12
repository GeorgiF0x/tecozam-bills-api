package com.tecozam.bills.admin.infrastructure.import_;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.Map;
import java.util.Optional;

/**
 * Contract común para los parsers de fila por proveedor.
 *
 * <p>Cada implementación lee las cabeceras (tolerando variantes de tildes y
 * mayúsculas) y traduce cada fila a un {@link FilaImportada}. Las
 * implementaciones son funciones puras: no leen ni escriben BD.
 */
public interface ListadoTarjetasRowParser {

    /**
     * Devuelve un mapa de clave lógica → índice de columna a partir de la fila 0.
     */
    Map<String, Integer> leerCabeceras(Sheet hoja);

    /**
     * Parsea una fila. Devuelve vacío si el número de tarjeta está en blanco
     * (fila vacía o de separación).
     */
    Optional<FilaImportada> parse(Row row, Map<String, Integer> headers);
}
