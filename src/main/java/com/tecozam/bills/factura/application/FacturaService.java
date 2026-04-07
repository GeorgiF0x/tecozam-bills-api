package com.tecozam.bills.factura.application;

import com.tecozam.bills.factura.domain.Factura;
import com.tecozam.bills.factura.domain.TarjetaResumen;
import com.tecozam.bills.factura.dto.FacturaDTO;
import com.tecozam.bills.factura.dto.ImportarFacturaResponse;
import com.tecozam.bills.factura.infrastructure.parser.FacturaParseResult;
import com.tecozam.bills.factura.infrastructure.parser.FacturaParser;
import com.tecozam.bills.factura.infrastructure.parser.FacturaParserFactory;
import com.tecozam.bills.factura.infrastructure.persistence.FacturaRepository;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.shared.infrastructure.storage.FileStorageService;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.ticket.application.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class FacturaService {

    private final FacturaRepository facturaRepository;
    private final ProveedorRepository proveedorRepository;
    private final TarjetaRepository tarjetaRepository;
    private final FacturaParserFactory parserFactory;
    private final FileStorageService fileStorageService;
    private final TicketService ticketService;

    /**
     * Importa una factura PDF (y opcionalmente un extracto) para un proveedor dado.
     *
     * @param proveedorId  ID del proveedor
     * @param facturaPdf   archivo PDF de la factura
     * @param extractoPdf  archivo PDF del extracto (opcional, sólo MOEVE)
     * @return estadísticas de la importación
     */
    public ImportarFacturaResponse importar(Long proveedorId, MultipartFile facturaPdf,
            MultipartFile extractoPdf) {

        // 1. Verificar proveedor
        Proveedor proveedor = proveedorRepository.findById(proveedorId)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", proveedorId));

        // 2. Validar PDF
        if (facturaPdf == null || facturaPdf.isEmpty()) {
            throw new IllegalArgumentException("El archivo de factura es obligatorio");
        }
        String contentType = facturaPdf.getContentType();
        if (contentType != null && !contentType.contains("pdf")
                && !facturaPdf.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("El archivo debe ser un PDF");
        }

        // 3. Parsear
        FacturaParser parser = parserFactory.getParser(proveedor.getCodigo());
        FacturaParseResult result;
        try {
            result = parser.parse(
                    facturaPdf.getInputStream(),
                    extractoPdf != null && !extractoPdf.isEmpty() ? extractoPdf.getInputStream() : null
            );
        } catch (Exception e) {
            log.error("[FacturaService] Error parseando PDF para proveedor {}: {}", proveedor.getCodigo(), e.getMessage(), e);
            throw new RuntimeException("Error al parsear la factura PDF: " + e.getMessage(), e);
        }

        Factura factura = result.getFactura();
        factura.setProveedor(proveedor);
        factura.setNombreCliente("TECOZAM SERVICIOS Y CONSTRUCCIONES S.L.");

        // 4. Verificar duplicado
        if (factura.getNumFactura() != null
                && facturaRepository.existsByNumFacturaAndProveedorId(factura.getNumFactura(), proveedorId)) {
            throw new DuplicateResourceException("Factura", "numFactura", factura.getNumFactura());
        }

        // 5. Guardar PDF físicamente
        try {
            String ruta = fileStorageService.saveFactura(facturaPdf, proveedor.getCodigo(),
                    factura.getNumFactura() != null ? factura.getNumFactura() : "sin_numero");
            factura.setRutaPdf(ruta);
        } catch (Exception e) {
            log.warn("[FacturaService] No se pudo guardar el PDF: {}", e.getMessage());
            // No es bloqueante — continuamos
        }

        // 6. Vincular tarjetas del maestro a los TarjetaResumen
        for (TarjetaResumen tr : factura.getTarjetaResumenes()) {
            if (tr.getNumTarjeta() != null) {
                Tarjeta tarjeta = tarjetaRepository.findByNumeroTarjeta(tr.getNumTarjeta())
                        .orElseGet(() -> crearTarjetaEnMaestro(tr.getNumTarjeta(), tr.getAlias(), proveedor));
                tr.setTarjeta(tarjeta);
            }
        }

        // 7. Persistir
        factura = facturaRepository.save(factura);

        // Estadísticas
        int numTarjetas    = factura.getTarjetaResumenes().size();
        int numOperaciones = factura.getTarjetaResumenes().stream()
                .mapToInt(tr -> tr.getOperaciones().size())
                .sum();

        log.info("[FacturaService] Factura {} importada: {} tarjetas, {} operaciones",
                factura.getNumFactura(), numTarjetas, numOperaciones);

        // 8. Re-cotejo asíncrono de tickets pendientes
        cotejarPendientesAsync();

        return new ImportarFacturaResponse(
                factura.getId(),
                factura.getNumFactura(),
                proveedor.getNombre(),
                numTarjetas,
                numOperaciones,
                factura.getRutaPdf(),
                "Factura importada correctamente"
        );
    }

    private void cotejarPendientesAsync() {
        try {
            ticketService.cotejarPendientes();
        } catch (Exception e) {
            log.warn("[FacturaService] Error en cotejo: {}", e.getMessage());
        }
    }

    /**
     * Lista facturas, opcionalmente filtradas por proveedor.
     */
    @Transactional(readOnly = true)
    public List<FacturaDTO> findAll(Long proveedorId) {
        List<Factura> facturas = proveedorId != null
                ? facturaRepository.findByProveedorIdOrderByFechaDesc(proveedorId)
                : facturaRepository.findAll();
        return facturas.stream().map(this::toDTO).toList();
    }

    /**
     * Obtiene una factura por ID.
     */
    @Transactional(readOnly = true)
    public FacturaDTO findById(Long id) {
        Factura factura = facturaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Factura", id));
        return toDTO(factura);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private Tarjeta crearTarjetaEnMaestro(String numTarjeta, String alias, Proveedor proveedor) {
        log.info("[FacturaService] Creando nueva tarjeta en maestro: {}", numTarjeta);
        Tarjeta tarjeta = Tarjeta.builder()
                .numeroTarjeta(numTarjeta)
                .alias(alias)
                .proveedor(proveedor)
                .activa(true)
                .build();
        return tarjetaRepository.save(tarjeta);
    }

    private FacturaDTO toDTO(Factura f) {
        int numTarjetas = f.getTarjetaResumenes() != null ? f.getTarjetaResumenes().size() : 0;
        int numOperaciones = f.getTarjetaResumenes() != null
                ? f.getTarjetaResumenes().stream().mapToInt(tr -> tr.getOperaciones().size()).sum()
                : 0;

        return new FacturaDTO(
                f.getId(),
                f.getProveedor() != null ? f.getProveedor().getId() : null,
                f.getProveedor() != null ? f.getProveedor().getNombre() : null,
                f.getNumFactura(),
                f.getFecha(),
                f.getPeriodoDesde(),
                f.getPeriodoHasta(),
                f.getNumCuenta(),
                f.getNifCliente(),
                f.getNombreCliente(),
                f.getBaseImponible(),
                f.getTotalIva(),
                f.getTotalFactura(),
                f.getVencimiento(),
                f.getRutaPdf(),
                f.getCreadoEn(),
                f.getCreadoPor(),
                numTarjetas,
                numOperaciones
        );
    }
}
