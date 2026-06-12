package com.tecozam.bills.admin.infrastructure.import_;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Devuelve el parser de filas adecuado para el proveedor del Excel cargado.
 *
 * <p>Calca el patrón de {@code FacturaParserFactory} ya existente en el módulo
 * factura/infrastructure/parser/ — esa decisión es deliberada para mantener
 * un único patrón de fábrica de strategies en todo el codebase.
 */
@Component
@RequiredArgsConstructor
public class ListadoTarjetasParserFactory {

    private static final Set<String> ALIAS_REPSOL = Set.of("REPSOL", "SOLRED");
    private static final Set<String> ALIAS_CEPSA = Set.of("CEPSA", "MOEVE", "MOEVE_CEPSA");

    private final RepsolXlsxRowParser repsolParser;
    private final CepsaXlsxRowParser cepsaParser;

    public ListadoTarjetasRowParser parserPara(String codigoProveedor) {
        if (codigoProveedor == null) {
            throw new IllegalArgumentException("El código de proveedor es obligatorio");
        }
        String codigo = codigoProveedor.trim().toUpperCase(Locale.ROOT);
        if (ALIAS_REPSOL.contains(codigo)) return repsolParser;
        if (ALIAS_CEPSA.contains(codigo)) return cepsaParser;
        throw new IllegalArgumentException("Proveedor sin parser de listado: " + codigoProveedor);
    }
}
