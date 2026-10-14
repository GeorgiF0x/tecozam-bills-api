package com.tecozam.bills.tarjeta.infrastructure.import_;

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
 * Lee las cabeceras y filas del Excel de alta masiva de tarjetas.
 *
 * <p>Funcion pura sin dependencias de BD: cada fila se traduce a una
 * {@link FilaTarjetaImportada}, aplicando las mismas reglas de validacion
 * que {@code TarjetaService.create} (numero de tarjeta y proveedor
 * obligatorios) para poder reportar un aviso legible por fila en vez de
 * abortar todo el archivo. La resolucion real del proveedor contra BD (y el
 * chequeo de activo) la hace {@code TarjetaImportService}, no este parser.
 *
 * <p>Cabeceras esperadas (en cualquier orden, tildes/mayusculas variables):
 * {@code NUMERO TARJETA | ALIAS | PROVEEDOR}.
 */
@Component
public final class TarjetaXlsxRowParser {

    private static final String KEY_NUMERO = "NUMERO_TARJETA";
    private static final String KEY_ALIAS = "ALIAS";
    private static final String KEY_PROVEEDOR = "PROVEEDOR";

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

    public Optional<FilaTarjetaImportada> parse(Row row, Map<String, Integer> headers) {
        if (row == null) return Optional.empty();

        String numeroTarjeta = leerCelda(row, headers, KEY_NUMERO);
        String alias = leerCelda(row, headers, KEY_ALIAS);
        String codigoProveedor = leerCelda(row, headers, KEY_PROVEEDOR);

        // Fila vacia (separador/salto de linea en el Excel) — se ignora en
        // silencio, no es un error.
        if (numeroTarjeta.isBlank() && alias.isBlank() && codigoProveedor.isBlank()) {
            return Optional.empty();
        }

        if (numeroTarjeta.isBlank()) {
            return Optional.of(new FilaTarjetaImportada(numeroTarjeta, alias, codigoProveedor,
                    "La columna Numero Tarjeta es obligatoria"));
        }
        if (codigoProveedor.isBlank()) {
            return Optional.of(new FilaTarjetaImportada(numeroTarjeta, alias, codigoProveedor,
                    "La columna Proveedor es obligatoria"));
        }

        return Optional.of(new FilaTarjetaImportada(numeroTarjeta, alias, codigoProveedor, null));
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
            case "NUMERO TARJETA", "NUMERO DE TARJETA" -> KEY_NUMERO;
            case "ALIAS" -> KEY_ALIAS;
            case "PROVEEDOR", "CODIGO PROVEEDOR", "CODIGO DE PROVEEDOR" -> KEY_PROVEEDOR;
            default -> null;
        };
    }
}
