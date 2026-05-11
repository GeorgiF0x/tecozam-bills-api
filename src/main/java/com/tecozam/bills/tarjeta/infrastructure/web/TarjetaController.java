package com.tecozam.bills.tarjeta.infrastructure.web;

import com.tecozam.bills.tarjeta.application.TarjetaService;
import com.tecozam.bills.tarjeta.dto.AsignarTarjetaRequest;
import com.tecozam.bills.tarjeta.dto.CreateTarjetaRequest;
import com.tecozam.bills.tarjeta.dto.GuardarPinRequest;
import com.tecozam.bills.tarjeta.dto.MiTarjetaDTO;
import com.tecozam.bills.tarjeta.dto.TarjetaAsignacionDTO;
import com.tecozam.bills.tarjeta.dto.TarjetaDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tarjetas")
@RequiredArgsConstructor
@Tag(name = "Tarjetas")
public class TarjetaController {

    private final TarjetaService tarjetaService;

    @GetMapping
    public List<TarjetaDTO> findAll(@RequestParam(defaultValue = "true") boolean activa) {
        return tarjetaService.findAll(activa);
    }

    @GetMapping("/{id}")
    public TarjetaDTO findById(@PathVariable Long id) {
        return tarjetaService.findById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public ResponseEntity<TarjetaDTO> create(@Valid @RequestBody CreateTarjetaRequest request) {
        return ResponseEntity.status(201).body(tarjetaService.create(request));
    }

    @PostMapping("/{id}/asignar")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public TarjetaDTO asignar(@PathVariable Long id, @Valid @RequestBody AsignarTarjetaRequest request) {
        return tarjetaService.asignar(id, request);
    }

    @PostMapping("/{id}/devolver")
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public TarjetaDTO devolver(@PathVariable Long id) {
        return tarjetaService.devolver(id);
    }

    @GetMapping("/{id}/asignaciones")
    public List<TarjetaAsignacionDTO> getHistorial(@PathVariable Long id) {
        return tarjetaService.getHistorial(id);
    }

    @PostMapping("/{id}/pin")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Guardar PIN", description = "Guarda o actualiza el PIN de la tarjeta. El conductor solo puede para sus tarjetas asignadas; ADMIN para cualquiera.")
    public ResponseEntity<Map<String, Object>> guardarPin(
            @PathVariable Long id,
            @Valid @RequestBody GuardarPinRequest request,
            Authentication authentication) {
        tarjetaService.guardarPin(id, request.pin(), authentication.getName());
        return ResponseEntity.ok(Map.of("success", true, "message", "PIN guardado"));
    }

    @GetMapping("/mis-tarjetas")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mis tarjetas", description = "Devuelve las tarjetas con asignación activa del trabajador autenticado.")
    public ResponseEntity<List<MiTarjetaDTO>> misTarjetas(Authentication authentication) {
        return ResponseEntity.ok(tarjetaService.findMisTarjetas(authentication.getName()));
    }
}
