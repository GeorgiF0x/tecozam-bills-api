package com.tecozam.bills.admin.infrastructure.import_;

import java.text.Normalizer;
import java.util.Set;

/**
 * Reconoce si el texto de la columna semántica de una fila del Excel
 * ({@code DES_PRODU} en Repsol, {@code CONCEPTOS} en Cepsa/Moeve) coincide con
 * alguno de los conceptos de combustible/servicio o de peaje/telepeaje ya
 * vistos en los ficheros reales de los proveedores.
 *
 * <p>Esto es puramente un indicador de calidad de dato para el admin (filas
 * con un concepto "raro" se cuentan en {@code filasIgnoradas} del reporte de
 * import para que revise el Excel) — NO decide si la fila es
 * {@link TipoRecurso#TARJETA} o {@link TipoRecurso#VIAT}. Esa decisión ahora
 * la toma el admin explícitamente al importar (checkbox "Este listado son
 * dispositivos VIAT"), ya que adivinar por palabras clave (aquí, o vía LLM)
 * demostró ser poco fiable: conceptos ambiguos como "COMISION AUTOPISTAS" en
 * una tarjeta de combustible normal se clasificaban como VIAT en falso
 * (ver odd/tasks/import-llm-switch.md).
 */
final class ConceptoRecognizer {

    private static final Set<String> KEYWORDS_PEAJE = Set.of(
            "PEAJE", "AUTOPISTA", "AUTOP", "TUNEL", "PORTAGEM",
            "USO RED",                  // "USO RED PORTUGAL/ESPAÑA"
            "OBE", "VIA T", "VIAT",     // dispositivos de telepeaje (OBE / VIA-T)
            "TELEPEAJE"
    );

    private static final Set<String> KEYWORDS_COMBUSTIBLE = Set.of(
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

    private ConceptoRecognizer() {
        // Utility class — no instances.
    }

    /**
     * Indica si el concepto está explícitamente reconocido en una de las dos
     * listas (peajes o combustibles/servicios). Conceptos vacíos o que no
     * coincidan con ninguna se consideran "no conocidos" — se cuentan en
     * {@code filasIgnoradas} del DTO de reporte para que el admin pueda
     * revisar el Excel fuente.
     */
    public static boolean esConceptoConocido(String concepto) {
        if (concepto == null || concepto.isBlank()) return false;
        String normalizado = normalizar(concepto);
        for (String keyword : KEYWORDS_PEAJE) {
            if (normalizado.contains(keyword)) return true;
        }
        for (String keyword : KEYWORDS_COMBUSTIBLE) {
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
