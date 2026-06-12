package com.tecozam.bills.ticket.application;

import com.tecozam.bills.auth.domain.Usuario;
import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioRepository;
import com.tecozam.bills.factura.domain.Operacion;
import com.tecozam.bills.factura.infrastructure.persistence.OperacionRepository;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.domain.TarjetaAsignacion;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.ticket.domain.Ticket;
import com.tecozam.bills.ticket.dto.CotejoResultDTO;
import com.tecozam.bills.ticket.dto.CreateTicketManualRequest;
import com.tecozam.bills.ticket.dto.CreateTicketOcrValidadoRequest;
import com.tecozam.bills.ticket.dto.TicketDTO;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import com.tecozam.bills.vehiculo.domain.CategoriaRecurso;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import com.tecozam.bills.vehiculo.infrastructure.persistence.VehiculoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    private final TicketRepository ticketRepository;
    private final OperacionRepository operacionRepository;
    private final ProveedorRepository proveedorRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final TarjetaRepository tarjetaRepository;
    private final TarjetaAsignacionRepository tarjetaAsignacionRepository;
    private final VehiculoRepository vehiculoRepository;
    private final UsuarioRepository usuarioRepository;
    private final com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository usuarioCampoRepository;

    @Transactional(readOnly = true)
    public List<TicketDTO> findAll(String estadoCotejo) {
        List<Ticket> tickets = estadoCotejo != null
                ? ticketRepository.findByEstadoCotejo(estadoCotejo)
                : ticketRepository.findAll();
        return tickets.stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public TicketDTO findById(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        return toDTO(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketDTO> findMisTickets(String username) {
        Trabajador trabajador = resolveTrabajadorByUsername(username);
        if (trabajador == null) {
            log.warn("Usuario {} sin trabajador asociado, devolviendo lista vacía", username);
            return List.of();
        }
        return ticketRepository.findByTrabajadorId(trabajador.getId()).stream()
                .map(this::toDTO)
                .toList();
    }

    private Trabajador resolveTrabajadorByUsername(String username) {
        UsuarioCampo campo = usuarioCampoRepository.findByUsername(username).orElse(null);
        if (campo != null) {
            return campo.getTrabajador();
        }
        Usuario legacy = usuarioRepository.findByUsername(username).orElse(null);
        return legacy != null ? legacy.getTrabajador() : null;
    }

    public TicketDTO createManual(CreateTicketManualRequest req) {
        Ticket.TicketBuilder builder = Ticket.builder()
                .origen("MANUAL")
                .estadoCotejo("PENDIENTE")
                .estacion(req.estacion())
                .fechaHora(req.fechaHora())
                .importeTotal(req.importeTotal())
                .litros(req.litros())
                .precioLitro(req.precioLitro())
                .kms(req.kms())
                .concepto(req.concepto())
                .observaciones(req.observaciones());

        if (req.proveedorId() != null) {
            Proveedor proveedor = proveedorRepository.findById(req.proveedorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Proveedor", req.proveedorId()));
            builder.proveedor(proveedor);
        }

        if (req.trabajadorId() != null) {
            Trabajador trabajador = trabajadorRepository.findById(req.trabajadorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Trabajador", req.trabajadorId()));
            builder.trabajador(trabajador);
        }

        if (req.tarjetaId() != null) {
            Tarjeta tarjeta = tarjetaRepository.findById(req.tarjetaId())
                    .orElseThrow(() -> new ResourceNotFoundException("Tarjeta", req.tarjetaId()));
            builder.tarjeta(tarjeta);
        }

        if (req.vehiculoId() != null) {
            Vehiculo vehiculo = vehiculoRepository.findById(req.vehiculoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vehiculo", req.vehiculoId()));
            builder.vehiculo(vehiculo);
        }

        Ticket ticket = ticketRepository.save(builder.build());
        log.info("Ticket manual creado: id={}", ticket.getId());
        return toDTO(ticket);
    }

    public CotejoResultDTO cotejarPendientes() {
        List<Ticket> pendientes = ticketRepository.findByEstadoCotejo("PENDIENTE");

        // Find a default GESTOR user for auto-assignment of incidents
        Usuario gestorDefault = usuarioRepository.findAll().stream()
                .filter(u -> u.isActivo() && "GESTOR".equals(u.getRol().name()))
                .findFirst()
                .orElse(null);

        int cotejados = 0;
        int sinCoincidencia = 0;
        int multiples = 0;
        int incidencias = 0;

        for (Ticket ticket : pendientes) {
            LocalDateTime desde = ticket.getFechaHora().minusHours(2);
            LocalDateTime hasta = ticket.getFechaHora().plusHours(2);

            List<Operacion> candidatas;

            if (ticket.getNumTarjeta4ultimos() != null && !ticket.getNumTarjeta4ultimos().isBlank()) {
                candidatas = operacionRepository.findParaCotejoConTarjeta(
                        desde, hasta, ticket.getImporteTotal(), ticket.getNumTarjeta4ultimos());
            } else {
                candidatas = operacionRepository.findParaCotejo(
                        desde, hasta, ticket.getImporteTotal());
            }

            if (candidatas.isEmpty()) {
                // No match at all → try wider search (±24h, no amount filter)
                List<Operacion> wider = operacionRepository.findParaCotejo(
                        ticket.getFechaHora().minusHours(24),
                        ticket.getFechaHora().plusHours(24),
                        ticket.getImporteTotal());

                if (wider.isEmpty()) {
                    // No operation found → auto-incident
                    ticket.setEstadoCotejo("INCIDENCIA");
                    ticket.setTipoIncidencia("OPERACION_NO_EXISTE");
                    ticket.setObservaciones("Auto-detectado: no se encontró operación coincidente en ±24h");
                    if (gestorDefault != null) ticket.setAsignadoA(gestorDefault);
                    incidencias++;
                } else {
                    ticket.setEstadoCotejo("SIN_COINCIDENCIA");
                    sinCoincidencia++;
                }
            } else if (candidatas.size() == 1) {
                Operacion op = candidatas.get(0);
                ticket.setOperacionCotejada(op);

                // Check for discrepancies
                String discrepancia = detectarDiscrepancia(ticket, op);
                if (discrepancia != null) {
                    // Match found but with discrepancy → auto-incident
                    ticket.setEstadoCotejo("INCIDENCIA");
                    ticket.setTipoIncidencia(discrepancia);
                    ticket.setObservaciones("Auto-detectado: " + describeDiscrepancia(discrepancia, ticket, op));
                    if (gestorDefault != null) ticket.setAsignadoA(gestorDefault);
                    incidencias++;
                } else {
                    ticket.setEstadoCotejo("COTEJADO");
                    cotejados++;
                }
            } else {
                ticket.setEstadoCotejo("MULTIPLE");
                multiples++;
            }
        }

        ticketRepository.saveAll(pendientes);
        log.info("Cotejo completado: cotejados={}, sinCoincidencia={}, multiples={}, incidencias={}",
                cotejados, sinCoincidencia, multiples, incidencias);

        return new CotejoResultDTO(cotejados, 0, sinCoincidencia, incidencias, multiples);
    }

    /**
     * Detects discrepancies between a ticket and a matched operation.
     * Returns the tipo_incidencia or null if everything matches.
     */
    private String detectarDiscrepancia(Ticket ticket, Operacion operacion) {
        // Price check: >5% difference
        if (ticket.getImporteTotal() != null && operacion.getImporteTotal() != null) {
            double ticketAmt = ticket.getImporteTotal().doubleValue();
            double opAmt = operacion.getImporteTotal().doubleValue();
            if (opAmt > 0 && Math.abs(ticketAmt - opAmt) / opAmt > 0.05) {
                return "PRECIO_NO_CONCUERDA";
            }
        }

        // Liters check: >10% difference
        if (ticket.getLitros() != null && operacion.getCantidad() != null) {
            double ticketL = ticket.getLitros().doubleValue();
            double opL = operacion.getCantidad().doubleValue();
            if (opL > 0 && Math.abs(ticketL - opL) / opL > 0.10) {
                return "LITROS_NO_COINCIDEN";
            }
        }

        // Date check: >4h difference
        if (ticket.getFechaHora() != null && operacion.getFechaHora() != null) {
            long diffMinutes = Math.abs(
                    java.time.Duration.between(ticket.getFechaHora(), operacion.getFechaHora()).toMinutes());
            if (diffMinutes > 240) {
                return "FECHA_INCORRECTA";
            }
        }

        return null; // No discrepancy
    }

    private String describeDiscrepancia(String tipo, Ticket ticket, Operacion op) {
        return switch (tipo) {
            case "PRECIO_NO_CONCUERDA" -> String.format(
                    "Importe ticket: %.2f€ vs operación: %.2f€ (diferencia >5%%)",
                    ticket.getImporteTotal(), op.getImporteTotal());
            case "LITROS_NO_COINCIDEN" -> String.format(
                    "Litros ticket: %.2f vs operación: %.2f (diferencia >10%%)",
                    ticket.getLitros(), op.getCantidad());
            case "FECHA_INCORRECTA" -> String.format(
                    "Fecha ticket: %s vs operación: %s (diferencia >4h)",
                    ticket.getFechaHora(), op.getFechaHora());
            default -> tipo;
        };
    }

    public TicketDTO marcarIncidencia(Long id, String observaciones, String tipoIncidencia, Long asignadoAId) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        ticket.setEstadoCotejo("INCIDENCIA");
        if (observaciones != null && !observaciones.isBlank()) {
            ticket.setObservaciones(observaciones);
        }
        if (tipoIncidencia != null && !tipoIncidencia.isBlank()) {
            ticket.setTipoIncidencia(tipoIncidencia);
        }
        if (asignadoAId != null) {
            Usuario usuario = usuarioRepository.findById(asignadoAId)
                    .orElseThrow(() -> new ResourceNotFoundException("Usuario", asignadoAId));
            ticket.setAsignadoA(usuario);
        }
        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket marcado como incidencia: id={}", id);
        return toDTO(saved);
    }

    public TicketDTO vincularOperacion(Long id, Long operacionId) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        Operacion operacion = operacionRepository.findById(operacionId)
                .orElseThrow(() -> new ResourceNotFoundException("Operacion", operacionId));
        ticket.setOperacionCotejada(operacion);
        ticket.setEstadoCotejo("COTEJADO");
        Ticket saved = ticketRepository.save(ticket);
        log.info("Ticket {} vinculado manualmente a operacion {}", id, operacionId);
        return toDTO(saved);
    }

    public TicketDTO resolverIncidencia(Long id, String notasResolucion) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        if (notasResolucion != null && !notasResolucion.isBlank()) {
            ticket.setNotasResolucion(notasResolucion);
        }
        ticket.setResueltoEn(LocalDateTime.now());
        ticket.setEstadoCotejo(ticket.getOperacionCotejada() != null ? "COTEJADO" : "PENDIENTE");
        Ticket saved = ticketRepository.save(ticket);
        log.info("Incidencia resuelta en ticket: id={}", id);
        return toDTO(saved);
    }

    /**
     * Crea un ticket validado con PIN a partir de datos OCR ya extraídos.
     * Valida la asignación activa de tarjeta, el PIN y la categoría del recurso.
     * Intenta cotejo automático si el importe coincide con una operación pendiente.
     *
     * @param username     username del usuario autenticado
     * @param request      parámetros de validación (tarjetaId, pin, vehiculoId, etc.)
     * @param estacion     nombre de la estación detectado por OCR
     * @param fechaHora    fecha/hora detectada por OCR
     * @param importeTotal importe total detectado por OCR
     * @param litros       litros detectados por OCR (puede ser null)
     * @param precioLitro  precio por litro detectado por OCR (puede ser null)
     * @param producto     producto detectado por OCR (puede ser null)
     * @param numRecibo    número de recibo detectado por OCR (puede ser null)
     * @return TicketDTO del ticket creado
     */
    /**
     * Valida que el usuario tenga una asignacion activa de la tarjeta SIN crear ticket.
     * Se usa antes del OCR preview para evitar gastar OpenAI si la tarjeta no le corresponde.
     *
     * <p>FLEET-01: el PIN de la tarjeta ya no se valida en el flujo de subida de
     * tickets. El PIN existe unicamente a efectos de consulta para el operario.
     */
    @Transactional(readOnly = true)
    public void validarAsignacion(String username, Long tarjetaId) {
        Trabajador trabajador = usuarioCampoRepository.findByUsername(username)
                .map(u -> u.getTrabajador())
                .orElseGet(() -> usuarioRepository.findByUsername(username)
                        .map(Usuario::getTrabajador)
                        .orElse(null));
        if (trabajador == null) {
            throw new BusinessException("El usuario no tiene un trabajador asociado", "usuario");
        }

        tarjetaRepository.findById(tarjetaId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta", tarjetaId));

        tarjetaAsignacionRepository
                .findByTarjetaIdAndTrabajadorIdAndFechaHastaIsNull(tarjetaId, trabajador.getId())
                .orElseThrow(() -> new BusinessException(
                        "No tienes asignación activa para esta tarjeta", "tarjetaId"));
    }

    public TicketDTO createOcrValidado(
            String username,
            CreateTicketOcrValidadoRequest request,
            String estacion,
            LocalDateTime fechaHora,
            BigDecimal importeTotal,
            BigDecimal litros,
            BigDecimal precioLitro,
            String producto,
            String numRecibo) {

        // Resolve trabajador del usuario actual (busca primero en usuarios_campo, luego legacy)
        Trabajador trabajador = usuarioCampoRepository.findByUsername(username)
                .map(u -> u.getTrabajador())
                .orElseGet(() -> usuarioRepository.findByUsername(username)
                        .map(Usuario::getTrabajador)
                        .orElse(null));
        if (trabajador == null) {
            throw new BusinessException("El usuario no tiene un trabajador asociado", "usuario");
        }

        // Verify tarjeta assignment (FLEET-01: ya no se valida PIN en el flujo de subida)
        Tarjeta tarjeta = tarjetaRepository.findById(request.tarjetaId())
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta", request.tarjetaId()));

        tarjetaAsignacionRepository
                .findByTarjetaIdAndTrabajadorIdAndFechaHastaIsNull(request.tarjetaId(), trabajador.getId())
                .orElseThrow(() -> new BusinessException(
                        "No tienes asignación activa para esta tarjeta", "tarjetaId"));

        // Verify vehículo
        Vehiculo vehiculo = vehiculoRepository.findById(request.vehiculoId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehiculo", request.vehiculoId()));

        if (CategoriaRecurso.VEHICULO.equals(request.categoriaRecurso())) {
            if (vehiculo.getMatricula() == null || vehiculo.getMatricula().isBlank()) {
                throw new BusinessException("El vehículo seleccionado no tiene matrícula", "vehiculoId");
            }
        } else if (CategoriaRecurso.INDUSTRIAL_MAQUINARIA.equals(request.categoriaRecurso())) {
            if (vehiculo.getCodigoObra() == null || vehiculo.getCodigoObra().isBlank()) {
                throw new BusinessException("El recurso industrial no tiene código de obra", "vehiculoId");
            }
        }

        // Build and save ticket
        String observacionesTicket = "OCR validado con PIN"
                + (numRecibo != null && !numRecibo.isBlank() ? " — Recibo: " + numRecibo : "");

        Ticket ticket = Ticket.builder()
                .origen("OCR_VALIDADO")
                .estadoCotejo("PENDIENTE")
                .trabajador(trabajador)
                .tarjeta(tarjeta)
                .vehiculo(vehiculo)
                .estacion(estacion.isBlank() ? "OCR" : estacion)
                .fechaHora(fechaHora)
                .importeTotal(importeTotal)
                .litros(litros)
                .precioLitro(precioLitro)
                .kms(request.kilometros())
                .concepto(producto)
                .observaciones(observacionesTicket)
                .build();

        ticket = ticketRepository.save(ticket);
        log.info("[OCR-VALIDADO] Ticket creado: id={} tarjeta={} trabajador={}", ticket.getId(),
                request.tarjetaId(), trabajador.getId());

        // Attempt auto-cotejo
        try {
            LocalDateTime desde = fechaHora.minusHours(2);
            LocalDateTime hasta = fechaHora.plusHours(2);
            String ultimos4 = tarjeta.getNumeroTarjeta();
            if (ultimos4.length() >= 4) ultimos4 = ultimos4.substring(ultimos4.length() - 4);

            List<Operacion> candidatas = operacionRepository.findParaCotejoConTarjeta(
                    desde, hasta, importeTotal, ultimos4);

            if (candidatas.size() == 1) {
                Operacion op = candidatas.get(0);
                String discrepancia = detectarDiscrepancia(ticket, op);
                if (discrepancia == null) {
                    ticket.setOperacionCotejada(op);
                    ticket.setEstadoCotejo("COTEJADO");
                    ticket = ticketRepository.save(ticket);
                    log.info("[OCR-VALIDADO] Cotejo automático exitoso: ticket={} operacion={}", ticket.getId(), op.getId());
                } else {
                    log.info("[OCR-VALIDADO] Discrepancia en cotejo automático: ticket={} tipo={}", ticket.getId(), discrepancia);
                }
            }
        } catch (Exception e) {
            log.warn("[OCR-VALIDADO] Error en cotejo automático para ticket={}: {}", ticket.getId(), e.getMessage());
        }

        return toDTO(ticket);
    }

    private TicketDTO toDTO(Ticket t) {
        return new TicketDTO(
                t.getId(),
                t.getOrigen(),
                t.getProveedor() != null ? t.getProveedor().getId() : null,
                t.getProveedor() != null ? t.getProveedor().getNombre() : null,
                t.getTrabajador() != null ? t.getTrabajador().getId() : null,
                t.getTrabajador() != null ? t.getTrabajador().getNombre() + " " + t.getTrabajador().getApellidos() : null,
                t.getTarjeta() != null ? t.getTarjeta().getId() : null,
                t.getVehiculo() != null ? t.getVehiculo().getId() : null,
                t.getEstacion(),
                t.getDireccion(),
                t.getFechaHora(),
                t.getNumTarjeta4ultimos(),
                t.getMatricula(),
                t.getKms(),
                t.getProducto(),
                t.getLitros(),
                t.getPrecioLitro(),
                t.getImporteTotal(),
                t.getNumRecibo(),
                t.getNifEstacion(),
                t.getImagenUrl(),
                t.getConcepto(),
                t.getObservaciones(),
                t.getEstadoCotejo(),
                t.getOperacionCotejada() != null ? t.getOperacionCotejada().getId() : null,
                t.getTipoIncidencia(),
                t.getAsignadoA() != null ? t.getAsignadoA().getId() : null,
                t.getAsignadoA() != null ? t.getAsignadoA().getUsername() : null,
                t.getNotasResolucion(),
                t.getResueltoEn(),
                t.getCreadoEn()
        );
    }
}
