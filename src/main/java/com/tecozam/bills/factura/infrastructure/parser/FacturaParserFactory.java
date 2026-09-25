package com.tecozam.bills.factura.infrastructure.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FacturaParserFactory {

    private final RepsolFacturaParser repsolParser;
    private final MoeveFacturaParser moeveParser;
    private final LlmFacturaParserFactory llmFacturaParserFactory;

    /**
     * @param modoLlmActivo cuando es {@code true} (interruptor centralizado,
     *                       ver odd/tasks/import-llm-switch.md), devuelve un
     *                       parser LLM en vez del determinista, para cualquier
     *                       proveedor soportado.
     */
    public FacturaParser getParser(String codigoProveedor, boolean modoLlmActivo) {
        validarProveedorSoportado(codigoProveedor);
        if (modoLlmActivo) {
            return llmFacturaParserFactory.crearPara(codigoProveedor);
        }
        return switch (codigoProveedor.toUpperCase()) {
            case "REPSOL", "SOLRED" -> repsolParser;
            case "MOEVE", "MOEVE_CEPSA", "CEPSA" -> moeveParser;
            default -> throw new IllegalArgumentException("Proveedor sin parser: " + codigoProveedor);
        };
    }

    private void validarProveedorSoportado(String codigoProveedor) {
        switch (codigoProveedor.toUpperCase()) {
            case "REPSOL", "SOLRED", "MOEVE", "MOEVE_CEPSA", "CEPSA" -> { /* soportado */ }
            default -> throw new IllegalArgumentException("Proveedor sin parser: " + codigoProveedor);
        }
    }
}
