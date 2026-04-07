package com.tecozam.bills.factura.infrastructure.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FacturaParserFactory {

    private final RepsolFacturaParser repsolParser;
    private final MoeveFacturaParser moeveParser;

    public FacturaParser getParser(String codigoProveedor) {
        return switch (codigoProveedor.toUpperCase()) {
            case "REPSOL", "SOLRED" -> repsolParser;
            case "MOEVE", "MOEVE_CEPSA", "CEPSA" -> moeveParser;
            default -> throw new IllegalArgumentException("Proveedor sin parser: " + codigoProveedor);
        };
    }
}
