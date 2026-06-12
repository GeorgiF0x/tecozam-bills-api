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
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.shared.infrastructure.storage.FileStorageService;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.domain.TarjetaNumeroNormalizer;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
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
    private final TarjetaAsignacionRepository tarjetaAsignacionRepository;
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

        // 3. Validacion previa de extracto obligatorio para MOEVE/CEPSA
        String codigoUpper = proveedor.getCodigo() == null ? "" : proveedor.getCodigo().toUpperCase();
        boolean requiereExtracto = codigoUpper.contains("MOEVE") || codigoUpper.contains("CEPSA");
        boolean tieneExtracto = extractoPdf != null && !extractoPdf.isEmpty();
        if (requiereExtracto && !tieneExtracto) {
            throw new BusinessException(
                    "Para facturas de " + proveedor.getNombre()
                            + " es obligatorio adjuntar el extracto PDF junto con la factura.");
        }

        // 4. Parsear
        FacturaParser parser = parserFactory.getParser(proveedor.getCodigo());
        FacturaParseResult result;
        try {
            result = parser.parse(
                    facturaPdf.getInputStream(),
                    tieneExtracto ? extractoPdf.getInputStream() : null
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[FacturaService] Error parseando PDF para proveedor {}: {}", proveedor.getCodigo(), e.getMessage(), e);
            throw new BusinessException(
                    "No se pudo procesar el PDF de la factura. Verifica que el archivo sea correcto y vuelve a intentarlo."
                            + " Detalle tecnico: " + e.getMessage());
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
        // Aplicamos TarjetaNumeroNormalizer para que el numero canonico sea el mismo
        // entre import de Excel y factura PDF (resuelve BILLS-11: duplicados por
        // normalizacion divergente).
        for (TarjetaResumen tr : factura.getTarjetaResumenes()) {
            if (tr.getNumTarjeta() != null) {
                String canonico = TarjetaNumeroNormalizer.canonical(tr.getNumTarjeta(), proveedor.getCodigo());
                tr.setNumTarjeta(canonico);
                Tarjeta tarjeta = tarjetaRepository.findByNumeroTarjeta(canonico)
                        .orElseGet(() -> crearTarjetaEnMaestro(canonico, tr.getAlias(), proveedor));
                tr.setTarjeta(tarjeta);

                // NEW-12: si la tarjeta tiene una asignación activa en el maestro,
                // copiamos el nombre del trabajador como conductor del resumen para
                // que se vea en el detalle de la factura sin tener que consultar el
                // maestro aparte.
                if (tr.getConductor() == null || tr.getConductor().isBlank()) {
                    tarjetaAsignacionRepository
                            .findByTarjetaIdAndFechaHastaIsNull(tarjeta.getId())
                            .ifPresent(asig -> {
                                Trabajador t = asig.getTrabajador();
                                if (t != null) {
                                    String nombreCompleto = (t.getNombre() == null ? "" : t.getNombre())
                                            + " "
                                            + (t.getApellidos() == null ? "" : t.getApellidos());
                                    tr.setConductor(nombreCompleto.trim());
                                }
                            });
                }
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

    /**
     * Elimina una factura junto con sus tarjetaResumenes, operaciones y conceptos
     * (cascade JPA). Los tickets que estaban cotejados contra operaciones de esta
     * factura se desvinculan y vuelven al estado PENDIENTE para que puedan
     * recotejarse contra una factura futura. El PDF físico también se borra
     * best-effort (si falla, la transacción no se revierte).
     */
    public void eliminar(Long id) {
        Factura factura = facturaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Factura", id));

        int ticketsDesvinculados = ticketService.desvincularPorFactura(id);
        if (ticketsDesvinculados > 0) {
            log.info("[FacturaService] {} tickets desvinculados de factura {}",
                    ticketsDesvinculados, id);
        }

        String rutaPdf = factura.getRutaPdf();
        facturaRepository.delete(factura);

        if (rutaPdf != null && !rutaPdf.isBlank()) {
            try {
                fileStorageService.borrarFactura(rutaPdf);
            } catch (Exception e) {
                log.warn("[FacturaService] No se pudo borrar el PDF '{}': {}", rutaPdf, e.getMessage());
            }
        }
        log.info("[FacturaService] Factura {} eliminada (id={})", factura.getNumFactura(), id);
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
