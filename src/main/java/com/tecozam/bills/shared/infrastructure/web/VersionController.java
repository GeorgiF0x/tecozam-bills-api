package com.tecozam.bills.shared.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Versión desplegada del servidor, para que la web y la app muestren qué hay
 * instalado (el cliente despliega el backend a mano y los frontends desde sus
 * forks, así que pueden quedar desfasados). Requiere sesión, como el resto.
 */
@RestController
@RequestMapping("/api/version")
@Tag(name = "Version")
public class VersionController {

    private final String version;

    public VersionController(@Value("${app.version}") String version) {
        this.version = version;
    }

    @GetMapping
    @Operation(summary = "Versión desplegada del servidor",
            description = "Número de entrega, el mismo de la guía de actualización")
    public Map<String, String> version() {
        return Map.of("version", version);
    }
}
