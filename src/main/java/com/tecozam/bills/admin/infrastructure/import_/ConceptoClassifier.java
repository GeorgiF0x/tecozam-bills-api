package com.tecozam.bills.admin.infrastructure.import_;

import java.text.Normalizer;
import java.util.Set;

/**
 * Clasifica una fila del Excel como {@link TipoRecurso#TARJETA} o {@link TipoRecurso#VIAT}
 * a partir del texto de la columna semántica del proveedor:
 * {@code DES_PRODU} en Repsol y {@code CONCEPTOS} en Cepsa/Moeve.
 *
 * <p>La clasificación se basa en {@link #KEYWORDS_VIAT}, no en el prefijo del número
 * de tarjeta (origen del bug BILLS-04). Cualquier otro texto cae a
 * {@code TARJETA} por defecto. {@link #esConceptoConocido(String)} permite al
 * importer reportar las filas que cayeron al default sin estar en ninguno de
 * los dos sets — útil para el campo {@code filasIgnoradas} del DTO.
 */
public final class ConceptoClassifier {

    private static final Set<String> KEYWORDS_VIAT = Set.of(
            "PEAJE", "AUTOPISTA", "TUNEL", "PORTAGEM"
    );

    /**
     * Set parcial de conceptos típicos de combustible/staff que el cliente
     * confirmó como TARJETA. Sirve únicamente para distinguir "TARJETA conocido"
     * de "TARJETA por default + warning". No es exhaustivo: si en el Excel
     * aparece un concepto que no está aquí ni en {@code KEYWORDS_VIAT}, la
     * fila se procesa como TARJETA y se reporta en {@code filasIgnoradas}.
     */
    private static final Set<String> CONCEPTOS_TARJETA_CONOCIDOS = Set.of(
            "GASOLEO", "DIESEL STAR", "DIESEL OPTIMA", "DIESEL E+",
            "ECOBLUE", "ECOBLUE 10 LT", "SIN PLOMO", "OPTIMA 95",
            "GNA SEM PB 95", "GNA SEM PB 98",
            "ADBLUE", "ACEITES LUBES",
            "STAFF", "TIENDA", "ALMACEN",
            "OTRAS COMPRAS", "OTRAS COMPRAS REDUCIDO"
    );

    private ConceptoClassifier() {
        // Utility class — no instances.
    }

    /**
     * Devuelve el tipo de recurso al que corresponde la fila a partir del concepto.
     *
     * <p>Reglas:
     * <ul>
     *   <li>Si {@code concepto} es null o en blanco → {@code TARJETA} (default seguro).</li>
     *   <li>Si normalizando contiene alguna de las palabras de {@link #KEYWORDS_VIAT} → {@code VIAT}.</li>
     *   <li>En otro caso → {@code TARJETA}.</li>
     * </ul>
     */
    public static TipoRecurso clasificar(String concepto) {
        if (concepto == null || concepto.isBlank()) {
            return TipoRecurso.TARJETA;
        }
        String normalizado = normalizar(concepto);
        for (String keyword : KEYWORDS_VIAT) {
            if (normalizado.contains(keyword)) {
                return TipoRecurso.VIAT;
            }
        }
        return TipoRecurso.TARJETA;
    }

    /**
     * Indica si el concepto está explícitamente en el set de conceptos
     * conocidos (peajes o combustibles). Útil para distinguir un TARJETA por
     * default de un TARJETA reconocido — los primeros se cuentan en
     * {@code filasIgnoradas} del DTO de reporte.
     */
    public static boolean esConceptoConocido(String concepto) {
        if (concepto == null || concepto.isBlank()) return false;
        String normalizado = normalizar(concepto);
        if (CONCEPTOS_TARJETA_CONOCIDOS.contains(normalizado)) return true;
        for (String keyword : KEYWORDS_VIAT) {
            if (normalizado.contains(keyword)) return true;
        }
        return false;
    }

    private static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return sinTildes.trim().toUpperCase().replaceAll("\\s+", " ");
    }
}
