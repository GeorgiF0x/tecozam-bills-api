package com.tecozam.bills.admin.infrastructure.import_;

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
 * Lee las cabeceras y filas del Excel de listado de tarjetas Cepsa/Moeve.
 *
 * <p>Función pura sin dependencias Spring de datos: cada fila se traduce a un
 * {@link FilaImportada}. La persistencia (crear/buscar Trabajador, Tarjeta,
 * Viat) la hace el importer en capa superior con repos inyectados — eso
 * permite que este parser se teste sin mocks.
 *
 * <p>Anotado como {@link Component} para que el factory lo inyecte vía Spring.
 *
 * <p>Cabeceras esperadas (en cualquier orden y con tildes/espacios variables):
 * {@code NUMERO DE TARJETA | MATRICULA | NOMBRE | MATRICULA2 | CENTRO COSTE |
 * Columna1 | CONCEPTOS}. Columnas no mapeadas se ignoran en silencio.
 */
@Component
public final class CepsaXlsxRowParser implements ListadoTarjetasRowParser {

    static final String KEY_NUMERO = "NUMERO_TARJETA";
    static final String KEY_MATRICULA = "MATRICULA";
    static final String KEY_NOMBRE = "NOMBRE";
    static final String KEY_CENTRO = "CENTRO_COSTE";
    static final String KEY_CONCEPTO = "CONCEPTOS";

    private static final DataFormatter FORMATTER = new DataFormatter(new Locale("es", "ES"));

    @Override
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

    @Override
    public Optional<FilaImportada> parse(Row row, Map<String, Integer> headers) {
        if (row == null) return Optional.empty();
        String numero = leerCelda(row, headers, KEY_NUMERO);
        if (numero.isBlank()) return Optional.empty();

        String matricula = leerCelda(row, headers, KEY_MATRICULA);
        if ("SIN MATRICULA".equalsIgnoreCase(matricula.trim())) {
            matricula = "";
        }
        String nombre = leerCelda(row, headers, KEY_NOMBRE);
        String centro = leerCelda(row, headers, KEY_CENTRO);
        String concepto = leerCelda(row, headers, KEY_CONCEPTO);

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
            case "NUMERO DE TARJETA", "NUMERO TARJETA" -> KEY_NUMERO;
            case "MATRICULA" -> KEY_MATRICULA;
            case "NOMBRE" -> KEY_NOMBRE;
            case "CENTRO COSTE", "CENTRO DE COSTE" -> KEY_CENTRO;
            case "CONCEPTOS", "CONCEPTO" -> KEY_CONCEPTO;
            default -> null;
        };
    }
}
