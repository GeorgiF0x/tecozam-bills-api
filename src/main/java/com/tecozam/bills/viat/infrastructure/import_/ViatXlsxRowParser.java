package com.tecozam.bills.viat.infrastructure.import_;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lee las cabeceras y filas del Excel de import masivo de VIATs.
 *
 * <p>Funcion pura sin dependencias de BD: cada fila se traduce a una
 * {@link FilaViatImportada}, aplicando la misma regla de validacion que
 * {@code ViatService.create} (codigo obligatorio) para poder reportar un
 * aviso legible por fila en vez de abortar todo el archivo.
 *
 * <p>Cabeceras esperadas (en cualquier orden, tildes/mayusculas variables):
 * {@code CODIGO | NUMERO SERIE | DESCRIPCION}.
 */
@Component
public final class ViatXlsxRowParser {

    private static final String KEY_CODIGO = "CODIGO";
    private static final String KEY_NUMERO_SERIE = "NUMERO_SERIE";
    private static final String KEY_DESCRIPCION = "DESCRIPCION";

    private static final DataFormatter FORMATTER = new DataFormatter(new Locale("es", "ES"));

    public Map<String, Integer> leerCabeceras(Sheet hoja) {
        Row header = hoja.getRow(0);
        if (header == null) return Map.of();
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < header.getLastCellNum(); i++) {
            String key = mapearCabecera(FORMATTER.formatCellValue(header.getCell(i)));
            if (key != null) indices.putIfAbsent(key, i);
        }
        return indices;
    }

    public Optional<FilaViatImportada> parse(Row row, Map<String, Integer> headers) {
        if (row == null) return Optional.empty();

        String codigo = leerCelda(row, headers, KEY_CODIGO);
        String numeroSerie = leerCelda(row, headers, KEY_NUMERO_SERIE);
        String descripcion = leerCelda(row, headers, KEY_DESCRIPCION);

        // Fila vacia (separador/salto de linea en el Excel) — se ignora en
        // silencio, no es un error.
        if (codigo.isBlank() && numeroSerie.isBlank() && descripcion.isBlank()) {
            return Optional.empty();
        }

        if (codigo.isBlank()) {
            return Optional.of(new FilaViatImportada(codigo, numeroSerie, descripcion,
                    "La columna codigo es obligatoria"));
        }

        return Optional.of(new FilaViatImportada(codigo, numeroSerie, descripcion, null));
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
            case "CODIGO" -> KEY_CODIGO;
            case "NUMERO SERIE", "NUMERO DE SERIE" -> KEY_NUMERO_SERIE;
            case "DESCRIPCION" -> KEY_DESCRIPCION;
            default -> null;
        };
    }
}
