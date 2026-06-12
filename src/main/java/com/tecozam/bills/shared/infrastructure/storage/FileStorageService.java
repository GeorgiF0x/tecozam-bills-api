package com.tecozam.bills.shared.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${app.storage.path:./storage}")
    private String storagePath;

    public String saveFactura(MultipartFile file, String proveedor, String numFactura) throws IOException {
        LocalDate now = LocalDate.now();
        Path dir = Paths.get(storagePath, "facturas",
                proveedor.toLowerCase(),
                String.valueOf(now.getYear()),
                String.format("%02d", now.getMonthValue()));
        Files.createDirectories(dir);
        String filename = UUID.randomUUID() + "_" + numFactura.replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";
        Path target = dir.resolve(filename);
        file.transferTo(target);
        log.info("Factura guardada: {}", target);
        return target.toString();
    }

    /**
     * Borra el PDF de una factura del disco (NEW-09). Es best-effort: si el path
     * no existe o no se puede borrar, se loggea como warn pero NO se lanza
     * excepción — la fuente de verdad es la BD.
     */
    public void borrarFactura(String ruta) {
        if (ruta == null || ruta.isBlank()) return;
        try {
            Path path = Paths.get(ruta);
            boolean borrado = Files.deleteIfExists(path);
            if (borrado) {
                log.info("PDF de factura borrado: {}", path);
            } else {
                log.warn("PDF de factura no encontrado: {}", path);
            }
        } catch (IOException e) {
            log.warn("Error al borrar PDF '{}': {}", ruta, e.getMessage());
        }
    }
}
