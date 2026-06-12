package com.tecozam.bills.admin.infrastructure.import_;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lee las cabeceras y filas del Excel de listado de tarjetas Repsol/Solred.
 *
 * <p>Espejo de {@link CepsaXlsxRowParser}: misma estructura, distintas cabeceras.
 * Repsol usa {@code NUMERO TARJETA / MATRICULA / NOMBRE / CENTRO COSTE / DES_PRODU}.
 *
 * <p>Función pura: cada fila → {@link FilaImportada}. La materialización en
 * BD se hace en capa superior con repos inyectados.
 */
public final class RepsolXlsxRowParser {

    static final String KEY_NUMERO = "NUMERO_TARJETA";
    static final String KEY_MATRICULA = "MATRICULA";
    static final String KEY_NOMBRE = "NOMBRE";
    static final String KEY_CENTRO = "CENTRO_COSTE";
    static final String KEY_DES_PRODU = "DES_PRODU";

    private static final DataFormatter FORMATTER = new DataFormatter(new Locale("es", "ES"));

    public Map<String, Integer> leerCabeceras(Sheet hoja) {
        Row header = hoja.getRow(0);
        if (header == null) return Map.of();
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < header.getLastCellNum(); i++) {
            String raw = FORMATTER.formatCellValue(header.getCell(i));
            String key = mapearCabecera(raw);
            if (key != null) {
                indices.putIfAbsent(key, i);
            }
        }
        return indices;
    }

    public Optional<FilaImportada> parse(Row row, Map<String, Integer> headers) {
        if (row == null) return Optional.empty();
        String numero = leerCelda(row, headers, KEY_NUMERO);
        if (numero.isBlank()) return Optional.empty();

        String matricula = leerCelda(row, headers, KEY_MATRICULA);
        String nombre = leerCelda(row, headers, KEY_NOMBRE);
        String centro = leerCelda(row, headers, KEY_CENTRO);
        String concepto = leerCelda(row, headers, KEY_DES_PRODU);

        TipoRecurso tipo = ConceptoClassifier.clasificar(concepto);
        boolean conocido = ConceptoClassifier.esConceptoConocido(concepto);

        return Optional.of(new FilaImportada(numero, matricula, nombre, centro, concepto, tipo, conocido));
    }

    private String leerCelda(Row row, Map<String, Integer> headers, String key) {
        Integer idx = headers.get(key);
        if (idx == null) return "";
        String val = FORMATTER.formatCellValue(row.getCell(idx));
        return val == null ? "" : val.trim();
    }

    private String mapearCabecera(String raw) {
        if (raw == null) return null;
        String sinTildes = Normalizer.normalize(raw.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String compact = sinTildes.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return switch (compact) {
            case "NUMERO TARJETA", "NUMERO DE TARJETA", "Nº TARJETA" -> KEY_NUMERO;
            case "MATRICULA" -> KEY_MATRICULA;
            case "NOMBRE" -> KEY_NOMBRE;
            case "CENTRO COSTE", "CENTRO DE COSTE" -> KEY_CENTRO;
            case "DES_PRODU", "DESCRIPCION PRODUCTO", "DES PRODU", "DESPRODU" -> KEY_DES_PRODU;
            default -> null;
        };
    }
}
