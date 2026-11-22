package com.tecozam.bills.shared.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileStorageServiceTest {

    private FileStorageService service(Path tempDir) {
        FileStorageService s = new FileStorageService();
        ReflectionTestUtils.setField(s, "storagePath", tempDir.toString());
        return s;
    }

    @Test
    @DisplayName("saveFactura con un PDF guarda el archivo con extension .pdf")
    void saveFactura_pdf_guardaConExtensionPdf(@TempDir Path tempDir) throws IOException {
        MockMultipartFile pdf = new MockMultipartFile("facturaPdf", "factura.pdf", "application/pdf", "contenido".getBytes());

        String ruta = service(tempDir).saveFactura(pdf, "REPSOL", "FA-001");

        assertThat(ruta).endsWith(".pdf");
    }

    @Test
    @DisplayName("saveFactura con un Excel (.xlsx) guarda el archivo con extension .xlsx, no .pdf")
    void saveFactura_excel_guardaConExtensionXlsx(@TempDir Path tempDir) throws IOException {
        MockMultipartFile excel = new MockMultipartFile("facturaPdf", "factura.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "contenido".getBytes());

        String ruta = service(tempDir).saveFactura(excel, "REPSOL", "FA-002");

        assertThat(ruta).endsWith(".xlsx");
    }
}
