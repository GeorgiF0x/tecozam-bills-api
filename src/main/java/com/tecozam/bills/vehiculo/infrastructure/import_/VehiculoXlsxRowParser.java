package com.tecozam.bills.vehiculo.infrastructure.import_;

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
 * Lee las cabeceras y filas del Excel de import masivo de vehiculos.
 *
 * <p>Funcion pura sin dependencias de BD: cada fila se traduce a una
 * {@link FilaVehiculoImportada}, aplicando las mismas reglas de validacion
 * que {@code VehiculoService.create} (tipo obligatorio; matricula obligatoria
 * para categoria VEHICULO; codigoObra obligatorio para
 * INDUSTRIAL_MAQUINARIA) para poder reportar un aviso legible por fila en vez
 * de abortar todo el archivo.
 *
 * <p>Cabeceras esperadas (en cualquier orden, tildes/mayusculas variables):
 * {@code TIPO | MATRICULA | CODIGO OBRA | CATEGORIA | DESCRIPCION}.
 */
@Component
public final class VehiculoXlsxRowParser {

    private static final String KEY_TIPO = "TIPO";
    private static final String KEY_MATRICULA = "MATRICULA";
    private static final String KEY_CODIGO_OBRA = "CODIGO_OBRA";
    private static final String KEY_CATEGORIA = "CATEGORIA";
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

    public Optional<FilaVehiculoImportada> parse(Row row, Map<String, Integer> headers) {
        if (row == null) return Optional.empty();

        String tipo = leerCelda(row, headers, KEY_TIPO);
        String matricula = leerCelda(row, headers, KEY_MATRICULA);
        String codigoObra = leerCelda(row, headers, KEY_CODIGO_OBRA);
        String categoriaRaw = leerCelda(row, headers, KEY_CATEGORIA);
        String descripcion = leerCelda(row, headers, KEY_DESCRIPCION);

        // Fila vacia (separador/salto de linea en el Excel) — se ignora en
        // silencio, no es un error.
        if (tipo.isBlank() && matricula.isBlank() && codigoObra.isBlank() && descripcion.isBlank()) {
            return Optional.empty();
        }

        String categoria = categoriaRaw.isBlank() ? "VEHICULO" : categoriaRaw.toUpperCase(Locale.ROOT);
        if (!categoria.equals("VEHICULO") && !categoria.equals("INDUSTRIAL_MAQUINARIA")) {
            return Optional.of(new FilaVehiculoImportada(matricula, codigoObra, categoria, tipo, descripcion,
                    "Categoria '" + categoriaRaw + "' invalida (valores permitidos: VEHICULO, INDUSTRIAL_MAQUINARIA)"));
        }

        if (tipo.isBlank()) {
            return Optional.of(new FilaVehiculoImportada(matricula, codigoObra, categoria, tipo, descripcion,
                    "La columna Tipo es obligatoria"));
        }
        if (categoria.equals("VEHICULO") && matricula.isBlank()) {
            return Optional.of(new FilaVehiculoImportada(matricula, codigoObra, categoria, tipo, descripcion,
                    "La matricula es obligatoria para categoria VEHICULO"));
        }
        if (categoria.equals("INDUSTRIAL_MAQUINARIA") && codigoObra.isBlank()) {
            return Optional.of(new FilaVehiculoImportada(matricula, codigoObra, categoria, tipo, descripcion,
                    "El codigo de obra es obligatorio para categoria INDUSTRIAL_MAQUINARIA"));
        }

        return Optional.of(new FilaVehiculoImportada(matricula, codigoObra, categoria, tipo, descripcion, null));
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
            case "TIPO" -> KEY_TIPO;
            case "MATRICULA" -> KEY_MATRICULA;
            case "CODIGO OBRA", "CODIGO DE OBRA" -> KEY_CODIGO_OBRA;
            case "CATEGORIA" -> KEY_CATEGORIA;
            case "DESCRIPCION" -> KEY_DESCRIPCION;
            default -> null;
        };
    }
}
