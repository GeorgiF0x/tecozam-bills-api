package com.tecozam.bills.admin.infrastructure.web;

import com.tecozam.bills.admin.application.ConfiguracionImportService;
import com.tecozam.bills.admin.dto.ConfiguracionImportDTO;
import com.tecozam.bills.admin.dto.UpdateConfiguracionImportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/configuracion-import")
@RequiredArgsConstructor
@Tag(name = "Configuracion import")
public class ConfiguracionImportController {

    private final ConfiguracionImportService configuracionImportService;

    @GetMapping
    @Operation(summary = "Consultar modo de import", description = "Indica si el import de listados/facturas usa LLM o el parser determinista")
    public ConfiguracionImportDTO obtener() {
        return configuracionImportService.obtener();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cambiar modo de import", description = "Activa o desactiva el modo LLM para el import de listados y facturas/extracto")
    public ConfiguracionImportDTO actualizar(
            @Valid @RequestBody UpdateConfiguracionImportRequest request,
            Authentication authentication) {
        return configuracionImportService.actualizar(request.modoLlmActivo(), authentication.getName());
    }
}
