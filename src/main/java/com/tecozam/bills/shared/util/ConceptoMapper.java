package com.tecozam.bills.shared.util;

import com.tecozam.bills.shared.domain.enums.ConceptoUnificado;

import java.util.Locale;
import java.util.Map;

/**
 * Maps provider-specific concept strings (Repsol, MOEVE, etc.) to the
 * unified {@link ConceptoUnificado} enum.
 * <p>
 * All lookups are case-insensitive and trim whitespace.
 */
public final class ConceptoMapper {

    private ConceptoMapper() {
        // Utility class — no instantiation
    }

    private static final Map<String, ConceptoUnificado> MAPPING = Map.ofEntries(
            // ── Repsol / SOLRED ──────────────────────────────────
            entry("diesel e+", ConceptoUnificado.DIESEL),
            entry("diesel", ConceptoUnificado.DIESEL),
            entry("diesel e+10", ConceptoUnificado.DIESEL),
            entry("diesel e+ neotech (l)", ConceptoUnificado.DIESEL),
            entry("diesel e+5 (l)", ConceptoUnificado.DIESEL),
            entry("diesel e+10 neotech (l)", ConceptoUnificado.DIESEL),
            entry("dieselnexa origen100%r(l)", ConceptoUnificado.DIESEL),
            entry("gasoleo a", ConceptoUnificado.DIESEL),
            entry("efitec 95 n (l)", ConceptoUnificado.GASOLINA),
            entry("efitec 98 n (l)", ConceptoUnificado.GASOLINA),
            entry("gasolina 95", ConceptoUnificado.GASOLINA),
            entry("gasolina 98", ConceptoUnificado.GASOLINA),
            entry("adblue", ConceptoUnificado.ADBLUE),
            entry("adblue repsol (l)", ConceptoUnificado.ADBLUE),
            entry("autopistas", ConceptoUnificado.PEAJE),
            entry("comision autopistas", ConceptoUnificado.PEAJE),
            entry("peaje", ConceptoUnificado.PEAJE),
            entry("lavados/lubrics.", ConceptoUnificado.LUBRICANTE),
            entry("lubricantes", ConceptoUnificado.LUBRICANTE),
            entry("lavado", ConceptoUnificado.LAVADO),
            entry("lubricante", ConceptoUnificado.LUBRICANTE),
            entry("aceite", ConceptoUnificado.LUBRICANTE),
            entry("otros prod bombona gas", ConceptoUnificado.OTROS),
            entry("otros", ConceptoUnificado.OTROS),
            entry("descuento estacion", ConceptoUnificado.DESCUENTO),
            entry("descuento", ConceptoUnificado.DESCUENTO),
            entry("bonificacion", ConceptoUnificado.DESCUENTO),

            // ── MOEVE / CEPSA ────────────────────────────────────
            entry("diesel star", ConceptoUnificado.DIESEL),
            entry("diesel optima", ConceptoUnificado.DIESEL),
            entry("gasóleo a", ConceptoUnificado.DIESEL),
            entry("gasoleo a premium", ConceptoUnificado.DIESEL),
            entry("gasóleo a premium", ConceptoUnificado.DIESEL),
            entry("gasóleo premium", ConceptoUnificado.DIESEL),
            entry("gasoleo premium", ConceptoUnificado.DIESEL),
            entry("g. sin plomo 95", ConceptoUnificado.GASOLINA),
            entry("optima 95", ConceptoUnificado.GASOLINA),
            entry("gasolina sin plomo 95", ConceptoUnificado.GASOLINA),
            entry("gasolina sin plomo 98", ConceptoUnificado.GASOLINA),
            entry("gasolina efitec 95", ConceptoUnificado.GASOLINA),
            entry("gasolina efitec 98", ConceptoUnificado.GASOLINA),
            entry("ecoblue granel", ConceptoUnificado.ADBLUE),
            entry("ecoblue garrafa", ConceptoUnificado.ADBLUE),
            entry("peajes de autopistas/tuneles", ConceptoUnificado.PEAJE),
            entry("lavados", ConceptoUnificado.LAVADO),
            entry("descuento en factura", ConceptoUnificado.DESCUENTO),

            // ── Generic fallbacks ─────────────────────────────────
            entry("peajes", ConceptoUnificado.PEAJE)
    );

    /**
     * Resolves a provider-specific concept string to the unified enum.
     *
     * @param rawConcept the raw concept string from the provider
     * @return the unified concept, or {@link ConceptoUnificado#OTROS} if no match
     */
    public static ConceptoUnificado resolve(String rawConcept) {
        if (rawConcept == null || rawConcept.isBlank()) {
            return ConceptoUnificado.OTROS;
        }
        String normalized = rawConcept.strip().toLowerCase(Locale.ROOT);
        return MAPPING.getOrDefault(normalized, ConceptoUnificado.OTROS);
    }

    /**
     * Checks whether a raw concept string has a known mapping.
     */
    public static boolean isKnown(String rawConcept) {
        if (rawConcept == null || rawConcept.isBlank()) {
            return false;
        }
        return MAPPING.containsKey(rawConcept.strip().toLowerCase(Locale.ROOT));
    }

    private static Map.Entry<String, ConceptoUnificado> entry(String key, ConceptoUnificado value) {
        return Map.entry(key, value);
    }
}
