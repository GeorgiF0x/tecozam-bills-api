package com.tecozam.bills.admin.infrastructure.web;

import com.tecozam.bills.admin.application.ReclasificarViatsResponse;
import com.tecozam.bills.admin.application.ReclasificarViatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tarjetas")
@RequiredArgsConstructor
@Tag(name = "Admin Tarjetas")
public class ReclasificarViatsController {

    private final ReclasificarViatsService service;

    @PostMapping("/reclasificar-viats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mueve VIATs Cepsa mal categorizados al maestro Tarjeta (BILLS-04 retrofit)")
    public ResponseEntity<ReclasificarViatsResponse> reclasificar(
            @RequestParam(value = "dryRun", defaultValue = "true") boolean dryRun,
            Authentication auth) {
        String ejecutadoPor = auth != null ? auth.getName() : "anonimo";
        return ResponseEntity.ok(service.reclasificar(dryRun, ejecutadoPor));
    }
}
