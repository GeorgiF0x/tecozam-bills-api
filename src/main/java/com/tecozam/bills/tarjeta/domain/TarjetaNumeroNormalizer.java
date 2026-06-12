package com.tecozam.bills.tarjeta.domain;

import java.util.Set;

/**
 * Forma canónica del número de tarjeta, única por proveedor.
 *
 * <p>El Excel de Repsol guarda el número con prefijo {@code 0007} (19 caracteres),
 * mientras que el parser del PDF de Repsol extrae el número sin el prefijo
 * (15 caracteres). Sin una normalización compartida, el mismo número físico se
 * persiste con dos representaciones distintas y la tabla {@code tarjetas} acaba
 * con duplicados (BILLS-11).
 *
 * <p>Esta clase es el único punto del dominio que decide la forma canónica:
 * <ul>
 *   <li>Repsol: 15 dígitos sin prefijo {@code 0007}.</li>
 *   <li>Cepsa/Moeve: el número íntegro tal como aparece en su Excel/factura.</li>
 * </ul>
 *
 * <p>Las llamadas desde importer Excel, parsers PDF y {@code FacturaService}
 * deben pasar por aquí antes de buscar o persistir el número.
 */
public final class TarjetaNumeroNormalizer {

    private static final String PREFIJO_REPSOL = "0007";

    private static final Set<String> ALIAS_REPSOL = Set.of("REPSOL", "SOLRED");
    private static final Set<String> ALIAS_CEPSA = Set.of("CEPSA", "MOEVE", "MOEVE_CEPSA");

    private TarjetaNumeroNormalizer() {
        // Utility class — no instances.
    }

    /**
     * Devuelve la forma canónica del número de tarjeta para el proveedor indicado.
     *
     * @param raw    número tal como llega desde Excel, factura PDF, formulario o API.
     * @param codigoProveedor código del proveedor (case-insensitive). Acepta los alias
     *                        documentados {@link #ALIAS_REPSOL} y {@link #ALIAS_CEPSA}.
     * @return número canónico.
     * @throws IllegalArgumentException si {@code raw} es null o en blanco, si
     *                                  {@code codigoProveedor} es null o si no se
     *                                  reconoce el proveedor.
     */
    public static String canonical(String raw, String codigoProveedor) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("El número de tarjeta no puede estar vacío");
        }
        if (codigoProveedor == null) {
            throw new IllegalArgumentException("El código de proveedor es obligatorio");
        }
        String limpio = raw.replaceAll("\\s+", "");
        String proveedor = codigoProveedor.trim().toUpperCase();

        if (ALIAS_REPSOL.contains(proveedor)) {
            return limpio.startsWith(PREFIJO_REPSOL) ? limpio.substring(PREFIJO_REPSOL.length()) : limpio;
        }
        if (ALIAS_CEPSA.contains(proveedor)) {
            return limpio;
        }
        throw new IllegalArgumentException("Proveedor no reconocido para normalización: " + codigoProveedor);
    }
}
