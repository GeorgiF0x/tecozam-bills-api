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

    /**
     * Sub-strings que, si aparecen en el concepto normalizado, indican que la
     * fila es un dispositivo VIAT/telepeaje (no una tarjeta de combustible).
     */
    private static final Set<String> KEYWORDS_VIAT = Set.of(
            "PEAJE", "AUTOPISTA", "AUTOP", "TUNEL", "PORTAGEM",
            "USO RED",                  // "USO RED PORTUGAL/ESPAÑA"
            "OBE", "VIA T", "VIAT",     // dispositivos de telepeaje (OBE / VIA-T)
            "TELEPEAJE"
    );

    /**
     * Sub-strings que, si aparecen en el concepto, lo marcan como TARJETA de
     * combustible/servicio (no telepeaje). Lista ampliada para cubrir los
     * conceptos reales que aparecen en los Excel de Repsol y Cepsa/Moeve.
     *
     * <p>Se usa para distinguir "TARJETA reconocido" de "TARJETA por default";
     * los segundos se reportan en {@code filasIgnoradas} para que el admin
     * pueda revisar el Excel y limpiar la fuente.
     */
    private static final Set<String> KEYWORDS_TARJETA = Set.of(
            // combustibles
            "DIESEL", "GASOLEO", "DSL", "DIE E", "DIESELNEXR", "DIESEL NEXR",
            "GASOLINA", "GASOL", "SIN PLOMO", "OPTIMA", "EFITEC", "EFI",
            "GNA SEM PB", "GSL",
            "ECOBLUE", "ADBLUE", "ADB", "BLUE+GRANE", "BLUE GRANE",
            // lubricantes y aceites
            "LUBRIC", "LUBRIF", "ACEITE", "LUBES",
            // servicios estación
            "LAVADO", "ENGRASE", "TIENDA", "ALMACEN", "PARKING",
            // staff / compras generales
            "STAFF", "OTRAS COMPRAS", "OUTRAS COMPRAS",
            "OTROS PROD", "OTR BOMGAS",
            "SUBVENC", "SUBVENCION"
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
     * Indica si el concepto está explícitamente reconocido en una de las dos
     * listas (peajes o combustibles/servicios). Útil para distinguir un
     * TARJETA por default de un TARJETA reconocido — los primeros se cuentan
     * en {@code filasIgnoradas} del DTO de reporte.
     *
     * <p>Conceptos vacíos se consideran "no conocidos" pero el importer no
     * los reporta como ruido — son filas sin concepto en el Excel original.
     */
    public static boolean esConceptoConocido(String concepto) {
        if (concepto == null || concepto.isBlank()) return false;
        String normalizado = normalizar(concepto);
        for (String keyword : KEYWORDS_VIAT) {
            if (normalizado.contains(keyword)) return true;
        }
        for (String keyword : KEYWORDS_TARJETA) {
            if (normalizado.contains(keyword)) return true;
        }
        return false;
    }

    private static String normalizar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // NEW-12: quitar puntuación común (.,/:;-) para que conceptos como
        // "GNA. SEM PB 95", "GEST. SERV. AUTOP. ESPAÑA" o "LAVADO/ENGRASE"
        // matcheen con keywords escritas sin puntuación.
        String sinPuntuacion = sinTildes.replaceAll("[.,/:;_-]", " ");
        return sinPuntuacion.trim().toUpperCase().replaceAll("\\s+", " ");
    }
}
