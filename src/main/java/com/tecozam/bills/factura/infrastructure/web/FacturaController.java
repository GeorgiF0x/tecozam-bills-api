package com.tecozam.bills.factura.infrastructure.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tecozam.bills.factura.application.FacturaLoteImportService;
import com.tecozam.bills.factura.application.FacturaService;
import com.tecozam.bills.factura.dto.FacturaDTO;
import com.tecozam.bills.factura.dto.ImportarFacturaLoteItemDTO;
import com.tecozam.bills.factura.dto.ImportarFacturaLoteResultadoDTO;
import com.tecozam.bills.factura.dto.ImportarFacturaResponse;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/facturas")
@RequiredArgsConstructor
@Tag(name = "Facturas", description = "Importación y consulta de facturas de proveedores")
public class FacturaController {

    private final FacturaService facturaService;
    private final FacturaLoteImportService facturaLoteImportService;
    private final ObjectMapper objectMapper;

    /**
     * Importa una factura PDF, con extracto opcional para MOEVE.
     *
     * @param proveedorId ID del proveedor al que pertenece la factura
     * @param facturaPdf  PDF principal de la factura
     * @param extractoPdf PDF del extracto de operaciones (sólo MOEVE, opcional)
     */
    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public ResponseEntity<ImportarFacturaResponse> importar(
            @RequestParam Long proveedorId,
            @RequestPart("factura") MultipartFile facturaPdf,
            @RequestPart(value = "extracto", required = false) MultipartFile extractoPdf
    ) {
        ImportarFacturaResponse response = facturaService.importar(proveedorId, facturaPdf, extractoPdf);
        return ResponseEntity.ok(response);
    }

    /**
     * Importa varias facturas de golpe. Recibe una parte "items" con el
     * manifiesto JSON (lista de {@code {proveedorId, facturaKey, extractoKey}})
     * y una parte de archivo por cada {@code facturaKey}/{@code extractoKey}
     * referenciada en el manifiesto (ej. "factura_0", "extracto_0",
     * "factura_1", ...), resueltas por nombre vía
     * {@link MultipartHttpServletRequest#getFileMap()}. Un fallo en un item
     * no aborta el resto del lote — ver {@code ImportarFacturaLoteResultadoDTO}.
     */
    @PostMapping(value = "/importar-lote", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','GESTOR')")
    public ResponseEntity<List<ImportarFacturaLoteResultadoDTO>> importarLote(
            @RequestPart("items") MultipartFile itemsPart,
            MultipartHttpServletRequest request
    ) {
        List<ImportarFacturaLoteItemDTO> items;
        try {
            items = objectMapper.readValue(itemsPart.getInputStream(),
                    new TypeReference<List<ImportarFacturaLoteItemDTO>>() {});
        } catch (IOException e) {
            throw new BusinessException("El manifiesto 'items' no es un JSON valido: " + e.getMessage());
        }

        Map<String, MultipartFile> fileMap = request.getFileMap();
        List<ImportarFacturaLoteResultadoDTO> resultado = facturaLoteImportService.importarLote(items, fileMap);
        return ResponseEntity.ok(resultado);
    }

    /**
     * Lista todas las facturas, opcionalmente filtradas por proveedor.
     */
    @GetMapping
    public List<FacturaDTO> findAll(
            @RequestParam(required = false) Long proveedorId
    ) {
        return facturaService.findAll(proveedorId);
    }

    /**
     * Obtiene una factura por su ID.
     */
    @GetMapping("/{id}")
    public FacturaDTO findById(@PathVariable Long id) {
        return facturaService.findById(id);
    }

    /**
     * NEW-09: borra una factura junto con sus operaciones, tarjetaResumenes y
     * conceptos. Los tickets cotejados contra esta factura se desvinculan a
     * estado PENDIENTE. El PDF físico también se borra (best-effort).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        facturaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
