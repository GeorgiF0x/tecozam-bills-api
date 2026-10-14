package com.tecozam.bills.trabajador.infrastructure.web;

import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.trabajador.application.TrabajadorImportService;
import com.tecozam.bills.trabajador.dto.ImportTrabajadoresReportDTO;
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
public class TrabajadorImportController {

    private final TrabajadorImportService service;

    @PostMapping(value = "/trabajadores", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Importa trabajadores en masa desde un Excel (.xlsx)",
            description = "Columnas: Nombre (obligatoria), Apellidos (obligatoria), Email, Dni/Nie, Origen "
                    + "(OFICINA/CAMPO/IMPORTACION, por defecto IMPORTACION si falta o no es reconocido).")
    public ResponseEntity<ImportTrabajadoresReportDTO> importar(@RequestPart("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("El archivo debe ser un .xlsx");
        }
        return ResponseEntity.ok(service.importar(file));
    }
}
