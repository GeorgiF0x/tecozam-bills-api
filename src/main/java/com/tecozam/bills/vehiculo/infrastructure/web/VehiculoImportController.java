package com.tecozam.bills.vehiculo.infrastructure.web;

import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.vehiculo.application.VehiculoImportService;
import com.tecozam.bills.vehiculo.dto.ImportVehiculosReportDTO;
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
public class VehiculoImportController {

    private final VehiculoImportService service;

    @PostMapping(value = "/vehiculos", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Importa vehiculos en masa desde un Excel (.xlsx)",
            description = "Columnas: Tipo (obligatoria), Matricula, CodigoObra, Categoria, Descripcion. "
                    + "Matricula obligatoria para categoria VEHICULO; CodigoObra obligatorio para INDUSTRIAL_MAQUINARIA.")
    public ResponseEntity<ImportVehiculosReportDTO> importar(@RequestPart("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("El archivo debe ser un .xlsx");
        }
        return ResponseEntity.ok(service.importar(file));
    }
}
