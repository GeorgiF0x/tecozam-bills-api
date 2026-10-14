package com.tecozam.bills.auth.infrastructure.web;

import com.tecozam.bills.auth.application.UsuarioImportService;
import com.tecozam.bills.auth.dto.ImportUsuariosReportDTO;
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

@RestController
@RequestMapping("/api/admin/import")
@RequiredArgsConstructor
@Tag(name = "Admin Import")
public class UsuarioImportController {

    private final UsuarioImportService service;

    @PostMapping(value = "/usuarios", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Importa usuarios (oficina y campo) en masa desde un Excel (.xlsx)",
            description = "Columnas: Tipo (OFICINA/CAMPO, obligatoria), Username, Password, Nombre "
                    + "(todas obligatorias), Apellidos y Telefono (solo CAMPO), Email y Rol (solo OFICINA, "
                    + "Rol obligatorio para OFICINA), Dni (opcional en ambos).")
    public ResponseEntity<ImportUsuariosReportDTO> importar(@RequestPart("file") MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("El archivo debe ser un .xlsx");
        }
        return ResponseEntity.ok(service.importar(file));
    }
}
