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
     * Guarda una foto de ticket (T3 de odd/tasks/ticket-multi-foto.md — antes
     * las fotos de ticket se descartaban tras el OCR). {@code indice} es el
     * índice de la foto dentro del conjunto subido para ese ticket, solo para
     * que el nombre de fichero sea legible.
     */
    public String saveTicketFoto(MultipartFile file, Long ticketId, int indice) throws IOException {
        LocalDate now = LocalDate.now();
        Path dir = Paths.get(storagePath, "tickets",
                String.valueOf(now.getYear()),
                String.format("%02d", now.getMonthValue()));
        Files.createDirectories(dir);
        String filename = "ticket_" + ticketId + "_" + indice + "_" + UUID.randomUUID() + extensionFromMime(file.getContentType());
        Path target = dir.resolve(filename);
        file.transferTo(target);
        log.info("Foto de ticket guardada: {}", target);
        return target.toString();
    }

    private String extensionFromMime(String mimeType) {
        if (mimeType == null) return ".jpg";
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
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
