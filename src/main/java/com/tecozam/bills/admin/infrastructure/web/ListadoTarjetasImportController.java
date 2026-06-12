package com.tecozam.bills.admin.infrastructure.web;

import com.tecozam.bills.admin.application.ListadoTarjetasImportService;
import com.tecozam.bills.admin.dto.ImportTarjetasReportDTO;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoint para importar el Excel de listado de tarjetas con selección de proveedor.
 * Reemplaza al legacy {@code POST /api/admin/import/tarjetas} que asumía Repsol.
 */
@RestController
@RequestMapping("/api/admin/import")
@RequiredArgsConstructor
@Tag(name = "Admin Import")
public class ListadoTarjetasImportController {

    private final ListadoTarjetasImportService service;

    @PostMapping(value = "/listado-tarjetas", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Importa el listado de tarjetas de un proveedor (REPSOL, CEPSA, MOEVE, ...)")
    public ResponseEntity<ImportTarjetasReportDTO> importar(
            @RequestPart("file") MultipartFile file,
            @RequestParam("codigoProveedor") String codigoProveedor) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("El archivo debe ser un .xlsx");
        }
        return ResponseEntity.ok(service.importar(file, codigoProveedor));
    }
}
