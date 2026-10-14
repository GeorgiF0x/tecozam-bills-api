package com.tecozam.bills.trabajador.infrastructure.import_;

import com.tecozam.bills.trabajador.domain.OrigenTrabajador;
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
 * Lee las cabeceras y filas del Excel de import masivo de trabajadores.
 *
 * <p>Funcion pura sin dependencias de BD: cada fila se traduce a una
 * {@link FilaTrabajadorImportada}. Nombre y apellidos son obligatorios; el
 * resto de columnas son opcionales. La columna Origen admite OFICINA/CAMPO/
 * IMPORTACION (case-insensitive) — si falta o no es un valor reconocido, se
 * usa IMPORTACION por defecto en silencio (no es un error de fila).
 *
 * <p>Cabeceras esperadas (en cualquier orden, tildes/mayusculas variables):
 * {@code NOMBRE | APELLIDOS | EMAIL | DNI/NIE | ORIGEN}.
 */
@Component
public final class TrabajadorXlsxRowParser {

    private static final String KEY_NOMBRE = "NOMBRE";
    private static final String KEY_APELLIDOS = "APELLIDOS";
    private static final String KEY_EMAIL = "EMAIL";
    private static final String KEY_DNI_NIE = "DNI_NIE";
    private static final String KEY_ORIGEN = "ORIGEN";

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

    public Optional<FilaTrabajadorImportada> parse(Row row, Map<String, Integer> headers) {
        if (row == null) return Optional.empty();

        String nombre = leerCelda(row, headers, KEY_NOMBRE);
        String apellidos = leerCelda(row, headers, KEY_APELLIDOS);
        String email = leerCelda(row, headers, KEY_EMAIL);
        String dniNie = leerCelda(row, headers, KEY_DNI_NIE);
        String origenRaw = leerCelda(row, headers, KEY_ORIGEN);

        // Fila vacia (separador/salto de linea en el Excel) — se ignora en
        // silencio, no es un error.
        if (nombre.isBlank() && apellidos.isBlank() && email.isBlank() && dniNie.isBlank()) {
            return Optional.empty();
        }

        OrigenTrabajador origen = mapearOrigen(origenRaw);

        if (nombre.isBlank() || apellidos.isBlank()) {
            return Optional.of(new FilaTrabajadorImportada(nombre, apellidos, email, dniNie, origen,
                    "Nombre y apellidos son obligatorios"));
        }

        return Optional.of(new FilaTrabajadorImportada(nombre, apellidos, email, dniNie, origen, null));
    }

    private OrigenTrabajador mapearOrigen(String raw) {
        if (raw == null || raw.isBlank()) return OrigenTrabajador.IMPORTACION;
        try {
            return OrigenTrabajador.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OrigenTrabajador.IMPORTACION;
        }
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
            case "NOMBRE" -> KEY_NOMBRE;
            case "APELLIDOS" -> KEY_APELLIDOS;
            case "EMAIL", "CORREO" -> KEY_EMAIL;
            case "DNI/NIE", "DNI NIE", "DNI", "NIE" -> KEY_DNI_NIE;
            case "ORIGEN" -> KEY_ORIGEN;
            default -> null;
        };
    }
}
