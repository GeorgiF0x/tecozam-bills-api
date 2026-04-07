package com.tecozam.bills.factura.infrastructure.web;

import com.tecozam.bills.factura.application.FacturaService;
import com.tecozam.bills.factura.dto.FacturaDTO;
import com.tecozam.bills.factura.dto.ImportarFacturaResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/facturas")
@RequiredArgsConstructor
@Tag(name = "Facturas", description = "Importación y consulta de facturas de proveedores")
public class FacturaController {

    private final FacturaService facturaService;

    /**
     * Importa una factura PDF, con extracto opcional para MOEVE.
     *
     * @param proveedorId ID del proveedor al que pertenece la factura
     * @param facturaPdf  PDF principal de la factura
     * @param extractoPdf PDF del extracto de operaciones (sólo MOEVE, opcional)
     */
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public ResponseEntity<ImportarFacturaResponse> importar(
            @RequestParam Long proveedorId,
            @RequestPart("factura") MultipartFile facturaPdf,
            @RequestPart(value = "extracto", required = false) MultipartFile extractoPdf
    ) {
        ImportarFacturaResponse response = facturaService.importar(proveedorId, facturaPdf, extractoPdf);
        return ResponseEntity.ok(response);
    }

    /**
     * Lista todas las facturas, opcionalmente filtradas por proveedor.
     */
    @GetMapping
    public List<FacturaDTO> findAll(
            @RequestParam(required = false) Long proveedorId
    ) {
        return facturaService.findAll(proveedorId);
    }

    /**
     * Obtiene una factura por su ID.
     */
    @GetMapping("/{id}")
    public FacturaDTO findById(@PathVariable Long id) {
        return facturaService.findById(id);
    }
}
