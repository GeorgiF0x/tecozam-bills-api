package com.tecozam.bills.factura.infrastructure.parser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Crea instancias de {@link LlmFacturaParser} con las credenciales OpenAI ya
 * configuradas (mismas properties que {@code AsistenteService}: reutiliza el
 * mismo proveedor/clave, sin gestión de credenciales nueva). No expone el
 * parser como {@code @Component} singleton porque necesita el código de
 * proveedor por importación para construir el prompt.
 */
@Component
public class LlmFacturaParserFactory {

    @Value("${app.openai.api-key:}")
    private String apiKey;

    @Value("${app.openai.model:gpt-4.1-mini}")
    private String model;

    public LlmFacturaParser crearPara(String codigoProveedor) {
        return new LlmFacturaParser(codigoProveedor, apiKey, model);
    }
}
