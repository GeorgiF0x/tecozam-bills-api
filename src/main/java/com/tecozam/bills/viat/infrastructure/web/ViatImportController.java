package com.tecozam.bills.viat.infrastructure.web;

import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.viat.application.ViatImportService;
import com.tecozam.bills.viat.dto.ImportViatsReportDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/import")
@RequiredArgsConstructor
@Tag(name = "Admin Import")
public class ViatImportController {

    private final ViatImportService service;

    @PostMapping(value = "/viats", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Importa VIATs en masa desde un Excel (.xlsx)",
            description = "Columnas: Codigo (obligatoria, unica), Numero Serie, Descripcion.")
    public ResponseEntity<ImportViatsReportDTO> importar(@RequestPart("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("El archivo debe ser un .xlsx");
        }
        return ResponseEntity.ok(service.importar(file));
    }
}
