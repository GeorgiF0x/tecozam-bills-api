package com.tecozam.bills.alerta.infrastructure.web;

import com.tecozam.bills.alerta.application.AlertaService;
import com.tecozam.bills.alerta.dto.AlertaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alertas")
@RequiredArgsConstructor
@Tag(name = "Alertas", description = "Gestión de alertas de préstamos")
public class AlertaController {

    private final AlertaService alertaService;

    @GetMapping("/pendientes")
    @Operation(summary = "Listar alertas pendientes", description = "Obtiene todas las alertas no leídas ordenadas por fecha")
    public ResponseEntity<List<AlertaDTO>> findPendientes() {
        return ResponseEntity.ok(alertaService.findPendientes());
    }

    @GetMapping("/count")
    @Operation(summary = "Contar alertas pendientes", description = "Retorna el número de alertas no leídas")
    public ResponseEntity<Long> count() {
        return ResponseEntity.ok(alertaService.countPendientes());
    }

    @PatchMapping("/{id}/leida")
    @Operation(summary = "Marcar alerta como leída", description = "Marca una alerta específica como leída")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id) {
        alertaService.marcarLeida(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/leer-todas")
    @Operation(summary = "Marcar todas como leídas", description = "Marca todas las alertas pendientes como leídas")
    public ResponseEntity<Void> marcarTodasLeidas() {
        alertaService.marcarTodasLeidas();
        return ResponseEntity.noContent().build();
    }
}
