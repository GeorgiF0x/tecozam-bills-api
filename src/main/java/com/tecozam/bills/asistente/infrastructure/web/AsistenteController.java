package com.tecozam.bills.asistente.infrastructure.web;

import com.tecozam.bills.asistente.application.AsistenteService;
import com.tecozam.bills.asistente.application.AsistenteToolService;
import com.tecozam.bills.asistente.dto.AsistenteRequest;
import com.tecozam.bills.asistente.dto.AsistenteResponse;
import com.tecozam.bills.asistente.dto.ChatToolsRequest;
import com.tecozam.bills.asistente.dto.ChatToolsResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/asistente")
@RequiredArgsConstructor
@Tag(name = "Asistente", description = "Asistente IA para consultas sobre el sistema")
public class AsistenteController {

    private final AsistenteService asistenteService;
    private final AsistenteToolService asistenteToolService;

    @PostMapping("/consulta")
    public ResponseEntity<AsistenteResponse> consulta(@Valid @RequestBody AsistenteRequest request) {
        AsistenteResponse response = asistenteService.consultar(request.query());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/chat-tools")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTOR')")
    public ChatToolsResponse chatTools(@RequestBody ChatToolsRequest request) {
        return asistenteToolService.chat(request);
    }
}
