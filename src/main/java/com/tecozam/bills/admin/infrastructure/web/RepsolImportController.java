package com.tecozam.bills.admin.infrastructure.web;

import com.tecozam.bills.admin.application.RepsolImportService;
import com.tecozam.bills.admin.dto.ImportRepsolReportDTO;
import com.tecozam.bills.admin.dto.ImportTarjetasReportDTO;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
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

import java.io.InputStream;

@RestController
@RequestMapping("/api/admin/import")
@RequiredArgsConstructor
@Tag(name = "Admin Import")
public class RepsolImportController {

    private final RepsolImportService service;

    @PostMapping(value = "/repsol", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Importa el Excel maestro de Repsol (DATOS MATRICULA + meses Q1)")
    public ResponseEntity<ImportRepsolReportDTO> importRepsol(@RequestPart("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new BusinessException("Archivo vacío");
        }
        try (InputStream in = file.getInputStream()) {
            return ResponseEntity.ok(service.importExcel(in));
        }
    }

    @PostMapping(value = "/tarjetas", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Importa el listado plano de tarjetas Repsol (LISTADO TARJETAS REPSOL.xlsx)")
    public ResponseEntity<ImportTarjetasReportDTO> importTarjetas(@RequestPart("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new BusinessException("Archivo vacío");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("El archivo debe ser un .xlsx");
        }
        try (InputStream in = file.getInputStream()) {
            return ResponseEntity.ok(service.importTarjetasListado(in));
        }
    }
}
