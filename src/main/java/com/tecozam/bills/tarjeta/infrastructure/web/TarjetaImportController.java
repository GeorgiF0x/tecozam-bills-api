package com.tecozam.bills.tarjeta.infrastructure.web;

import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.tarjeta.application.TarjetaImportService;
import com.tecozam.bills.tarjeta.dto.ImportTarjetasAltaReportDTO;
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
public class TarjetaImportController {

    private final TarjetaImportService service;

    @PostMapping(value = "/tarjetas", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Da de alta tarjetas en masa desde un Excel (.xlsx)",
            description = "Columnas: Numero Tarjeta (obligatoria, unica), Proveedor (obligatoria, codigo de un "
                    + "proveedor ya existente y activo), Alias (opcional).")
    public ResponseEntity<ImportTarjetasAltaReportDTO> importar(@RequestPart("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("El archivo debe ser un .xlsx");
        }
        return ResponseEntity.ok(service.importar(file));
    }
}
