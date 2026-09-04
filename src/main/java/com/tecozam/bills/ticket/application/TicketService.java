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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TicketService {

    /** Las facturas son mensuales: pasado este umbral sin cotejar, es un problema real, no falta de factura. */
    private static final long DIAS_LIMITE_SIN_COTEJAR = 30;

    /** Ventana para detectar fotos repetidas de la misma compra (ver {@link #verificarNoEsDuplicado}). */
    private static final long DUPLICADO_VENTANA_MINUTOS = 5;

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
                : ticketRepository.findAllActivos();
        return tickets.stream().map(this::toDTO).toList();
    }

    /**
     * Borrado logico (no fisico): conserva el ticket para auditoria pero deja
     * de aparecer en los listados normales. Solo ADMIN (verificado en el
     * controller).
     */
    public void eliminar(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
        ticket.softDelete();
        ticketRepository.save(ticket);
        log.info("Ticket {} eliminado (borrado logico)", id);
    }

    /**
     * Borrado logico en masa. Atomico: si algun id no existe, no se elimina
     * ninguno (se propaga ResourceNotFoundException y la transaccion revierte).
     */
    @Transactional
    public void eliminarMasivo(List<Long> ids) {
        for (Long id : ids) {
            Ticket ticket = ticketRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
            ticket.softDelete();
            ticketRepository.save(ticket);
        }
        log.info("Tickets {} eliminados (borrado logico masivo)", ids);
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
        verificarNoEsDuplicado(req.numTarjeta4ultimos(), req.fechaHora(), req.importeTotal());

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
                .observaciones(req.observaciones())
                .numTarjeta4ultimos(req.numTarjeta4ultimos())
                .matricula(req.matricula())
                .nifEstacion(req.nifEstacion())
                .direccion(req.direccion());

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

    /**
     * NEW-09: desvincula los tickets que estaban cotejados contra una factura
     * que se va a eliminar. Los tickets quedan en estado PENDIENTE y sin
     * operacion_cotejada para que vuelvan al cotejo automático con futuras
     * facturas.
     *
     * @return número de tickets afectados
     */
    public int desvincularPorFactura(Long facturaId) {
        List<Ticket> afectados = ticketRepository.findByOperacionCotejadaFacturaId(facturaId);
        for (Ticket t : afectados) {
            t.setOperacionCotejada(null);
            t.setEstadoCotejo("PENDIENTE");
        }
        ticketRepository.saveAll(afectados);
        return afectados.size();
    }

    public CotejoResultDTO cotejarPendientes() {
        List<Ticket> pendientes = ticketRepository.findByEstadoCotejo("PENDIENTE");
        Usuario gestorDefault = findGestorDefault();

        int cotejados = 0;
        int sinCoincidencia = 0;
        int multiples = 0;
        int incidencias = 0;

        for (Ticket ticket : pendientes) {
            LocalDateTime desde = ticket.getFechaHora().minusHours(2);
            LocalDateTime hasta = ticket.getFechaHora().plusHours(2);

            List<Operacion> candidatas = buscarCandidatas(ticket, desde, hasta);
            Operacion mejorCandidata = elegirMejorCandidata(candidatas, ticket);

            if (candidatas.isEmpty()) {
                // No match at all → try wider search (±24h). Mantiene la misma
                // prioridad tarjeta>importe que la busqueda estricta: antes esta
                // busqueda ampliada ignoraba la tarjeta por completo y podia
                // etiquetar SIN_COINCIDENCIA basandose en una operacion de OTRA
                // tarjeta que por casualidad caia cerca en fecha/importe.
                List<Operacion> wider = buscarCandidatas(ticket,
                        ticket.getFechaHora().minusHours(24),
                        ticket.getFechaHora().plusHours(24));

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
            } else if (mejorCandidata != null) {
                aplicarResultadoCotejo(ticket, mejorCandidata, gestorDefault);
                if ("COTEJADO".equals(ticket.getEstadoCotejo())) {
                    cotejados++;
                } else {
                    incidencias++;
                }
            } else {
                // Varias candidatas y ninguna claramente mas cercana en importe
                // que las demas: ambiguo de verdad, requiere revision manual.
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
     * Escala a incidencia los tickets que llevan más de {@link #DIAS_LIMITE_SIN_COTEJAR}
     * días sin cotejarse (PENDIENTE o SIN_COINCIDENCIA). Como las facturas son
     * mensuales, si ha pasado más de un mes desde la fecha de la operación sin
     * que aparezca una factura que la cubra, lo probable es un problema real
     * (tarjeta equivocada, factura de ese periodo nunca importada...), no que
     * "todavía no ha llegado la factura".
     */
    public int escalarTicketsAntiguos() {
        List<Ticket> candidatos = ticketRepository.findByEstadoCotejoIn(List.of("PENDIENTE", "SIN_COINCIDENCIA"));

        Usuario gestorDefault = usuarioRepository.findAll().stream()
                .filter(u -> u.isActivo() && "GESTOR".equals(u.getRol().name()))
                .findFirst()
                .orElse(null);

        LocalDateTime ahora = LocalDateTime.now();
        List<Ticket> escalados = new ArrayList<>();

        for (Ticket ticket : candidatos) {
            long dias = ChronoUnit.DAYS.between(ticket.getFechaHora(), ahora);
            if (dias > DIAS_LIMITE_SIN_COTEJAR) {
                ticket.setEstadoCotejo("INCIDENCIA");
                ticket.setTipoIncidencia("SIN_COTEJAR_1_MES");
                ticket.setObservaciones("Auto-detectado: han pasado más de " + DIAS_LIMITE_SIN_COTEJAR
                        + " días desde la operación sin encontrar una factura que la cubra");
                if (gestorDefault != null) ticket.setAsignadoA(gestorDefault);
                escalados.add(ticket);
            }
        }

        ticketRepository.saveAll(escalados);
        log.info("Escalado de tickets antiguos completado: {} tickets escalados a INCIDENCIA", escalados.size());
        return escalados.size();
    }

    /**
     * Umbral de discrepancia de fecha. Debe ser MENOR que la ventana de
     * busqueda de candidatas (+-2h = 120 min): con un umbral >=120 min este
     * chequeo era codigo muerto, nunca podia saltar porque toda candidata
     * encontrada ya estaba, por definicion de la busqueda, a <=120 min del
     * ticket (bug real: el umbral original era de 240 min).
     */
    private static final long MINUTOS_LIMITE_DIFERENCIA_FECHA = 90;

    /**
     * Detects discrepancies between a ticket and a matched operation.
     * Returns the tipo_incidencia or null if everything matches.
     */
    private String detectarDiscrepancia(Ticket ticket, Operacion operacion) {
        // Price check: >5% difference. Se divide por el VALOR ABSOLUTO del
        // importe de la operacion (no por el importe con signo): si algun
        // dia una operacion individual trae importe negativo (nota de
        // credito), dividir por un numero negativo invierte el signo de la
        // fraccion entera y el chequeo nunca saltaba pasase lo que pasase
        // (bug real, no detectado en datos actuales pero posible).
        if (ticket.getImporteTotal() != null && operacion.getImporteTotal() != null) {
            double ticketAmt = ticket.getImporteTotal().doubleValue();
            double opAmt = operacion.getImporteTotal().doubleValue();
            if (opAmt != 0 && Math.abs(ticketAmt - opAmt) / Math.abs(opAmt) > 0.05) {
                return "PRECIO_NO_CONCUERDA";
            }
        }

        // Liters check: >10% difference. Mismo razonamiento que el importe.
        if (ticket.getLitros() != null && operacion.getCantidad() != null) {
            double ticketL = ticket.getLitros().doubleValue();
            double opL = operacion.getCantidad().doubleValue();
            if (opL != 0 && Math.abs(ticketL - opL) / Math.abs(opL) > 0.10) {
                return "LITROS_NO_COINCIDEN";
            }
        }

        // Date check
        if (ticket.getFechaHora() != null && operacion.getFechaHora() != null) {
            long diffMinutes = Math.abs(
                    java.time.Duration.between(ticket.getFechaHora(), operacion.getFechaHora()).toMinutes());
            if (diffMinutes > MINUTOS_LIMITE_DIFERENCIA_FECHA) {
                return "FECHA_INCORRECTA";
            }
        }

        return null; // No discrepancy
    }

    /**
     * Vincula el ticket a la operacion candidata y decide COTEJADO vs
     * INCIDENCIA segun detectarDiscrepancia. Usado tanto por el cotejo por
     * lote como por el cotejo inmediato al subir un ticket (createOcrValidado)
     * — antes este segundo, al encontrar una discrepancia, se quedaba callado
     * dejando el ticket en PENDIENTE sin vincular nada hasta que alguien
     * pulsara "Re-cotejar" a mano (bug real).
     */
    private void aplicarResultadoCotejo(Ticket ticket, Operacion operacion, Usuario gestorDefault) {
        ticket.setOperacionCotejada(operacion);
        String discrepancia = detectarDiscrepancia(ticket, operacion);
        if (discrepancia != null) {
            ticket.setEstadoCotejo("INCIDENCIA");
            ticket.setTipoIncidencia(discrepancia);
            ticket.setObservaciones("Auto-detectado: " + describeDiscrepancia(discrepancia, ticket, operacion));
            if (gestorDefault != null) ticket.setAsignadoA(gestorDefault);
        } else {
            ticket.setEstadoCotejo("COTEJADO");
        }
    }

    private Usuario findGestorDefault() {
        return usuarioRepository.findAll().stream()
                .filter(u -> u.isActivo() && "GESTOR".equals(u.getRol().name()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Evita crear un ticket duplicado de una compra ya subida antes. Pensado
     * para el caso real de que un trabajador fotografia varios recibos de la
     * MISMA compra (recibo cliente + comprobante fiscal + copia comercio) y
     * cada foto se sube como ticket aparte: sin este control, los 2-3 tickets
     * acaban cotejados contra la misma operacion de la factura.
     *
     * Solo se activa si se conoce la tarjeta (sin tarjeta no hay señal fuerte
     * para distinguir un duplicado real de dos compras distintas parecidas).
     */
    private void verificarNoEsDuplicado(String ultimos4, LocalDateTime fechaHora, java.math.BigDecimal importeTotal) {
        if (ultimos4 == null || ultimos4.isBlank() || fechaHora == null || importeTotal == null) {
            return;
        }
        List<Ticket> duplicados = ticketRepository.findPosiblesDuplicados(
                ultimos4,
                fechaHora.minusMinutes(DUPLICADO_VENTANA_MINUTOS),
                fechaHora.plusMinutes(DUPLICADO_VENTANA_MINUTOS),
                importeTotal);
        if (!duplicados.isEmpty()) {
            throw new BusinessException(
                    "Ya existe un ticket con la misma tarjeta, fecha e importe — parece una foto repetida de la misma compra",
                    "duplicado");
        }
    }

    /**
     * Busca operaciones candidatas para el ticket en la ventana de fecha dada,
     * en orden de preferencia: tarjeta completa (exacta, sin riesgo de
     * colision de ultimos 4 digitos) > tarjeta por ultimos 4 (LIKE, cuando el
     * ticket no tiene una Tarjeta formal vinculada) > importe (si no hay
     * ninguna tarjeta conocida). Se reutiliza tanto para la busqueda estricta
     * (+-2h) como para la ampliada (+-24h) para que ambas respeten la misma
     * prioridad de señal.
     */
    private List<Operacion> buscarCandidatas(Ticket ticket, LocalDateTime desde, LocalDateTime hasta) {
        String numTarjetaCompleto = ticket.getTarjeta() != null ? ticket.getTarjeta().getNumeroTarjeta() : null;
        if (numTarjetaCompleto != null && !numTarjetaCompleto.isBlank()) {
            return operacionRepository.findParaCotejoConTarjetaExacta(desde, hasta, numTarjetaCompleto);
        }
        if (ticket.getNumTarjeta4ultimos() != null && !ticket.getNumTarjeta4ultimos().isBlank()) {
            return operacionRepository.findParaCotejoConTarjeta(desde, hasta, ticket.getNumTarjeta4ultimos());
        }
        return operacionRepository.findParaCotejo(desde, hasta, ticket.getImporteTotal());
    }

    /**
     * Elige la candidata a cotejar cuando la busqueda por tarjeta+fecha devuelve
     * mas de una operacion (p.ej. diesel + AdBlue repostados casi a la vez, misma
     * tarjeta). Se desempata por cercania de importe: si la mas cercana lo esta
     * claramente mas que la segunda (al menos el doble de cerca), se elige esa;
     * si estan empatadas o demasiado cerca entre si, es ambiguo de verdad y se
     * deja para revision manual (MULTIPLE) en vez de arriesgar un cotejo erroneo.
     */
    private Operacion elegirMejorCandidata(List<Operacion> candidatas, Ticket ticket) {
        if (candidatas.isEmpty()) {
            return null;
        }
        if (candidatas.size() == 1) {
            return candidatas.get(0);
        }

        List<Operacion> ordenadas = candidatas.stream()
                .sorted(Comparator.comparing(op -> diffImporte(op, ticket)))
                .toList();

        BigDecimal mejorDiff = diffImporte(ordenadas.get(0), ticket);
        BigDecimal segundaDiff = diffImporte(ordenadas.get(1), ticket);

        if (mejorDiff.compareTo(segundaDiff) == 0) {
            return null; // empate real, no hay forma de saber cual es
        }
        boolean claramenteMejor = segundaDiff.compareTo(mejorDiff.multiply(BigDecimal.valueOf(2))) >= 0;
        return claramenteMejor ? ordenadas.get(0) : null;
    }

    private BigDecimal diffImporte(Operacion op, Ticket ticket) {
        if (op.getImporteTotal() == null || ticket.getImporteTotal() == null) {
            return BigDecimal.valueOf(Long.MAX_VALUE);
        }
        return op.getImporteTotal().subtract(ticket.getImporteTotal()).abs();
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

        boolean yaVinculadaAOtroTicket = ticketRepository.findByOperacionCotejadaIdActivos(operacionId).stream()
                .anyMatch(t -> !t.getId().equals(id));
        if (yaVinculadaAOtroTicket) {
            throw new BusinessException(
                    "Esta operación ya está vinculada a otro ticket — desvincúlala primero si quieres reasignarla",
                    "operacionId");
        }

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
                .findActivaByTarjetaIdAndTrabajadorId(tarjetaId, trabajador.getId(), LocalDate.now())
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
                .findActivaByTarjetaIdAndTrabajadorId(request.tarjetaId(), trabajador.getId(), LocalDate.now())
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

        String ultimos4 = tarjeta.getNumeroTarjeta();
        if (ultimos4 != null && ultimos4.length() >= 4) {
            ultimos4 = ultimos4.substring(ultimos4.length() - 4);
        }

        verificarNoEsDuplicado(ultimos4, fechaHora, importeTotal);

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
                .numTarjeta4ultimos(ultimos4)
                .build();

        ticket = ticketRepository.save(ticket);
        log.info("[OCR-VALIDADO] Ticket creado: id={} tarjeta={} trabajador={}", ticket.getId(),
                request.tarjetaId(), trabajador.getId());

        // Attempt auto-cotejo
        try {
            LocalDateTime desde = fechaHora.minusHours(2);
            LocalDateTime hasta = fechaHora.plusHours(2);

            List<Operacion> candidatas = buscarCandidatas(ticket, desde, hasta);
            Operacion op = elegirMejorCandidata(candidatas, ticket);

            if (op != null) {
                aplicarResultadoCotejo(ticket, op, findGestorDefault());
                ticket = ticketRepository.save(ticket);
                log.info("[OCR-VALIDADO] Cotejo automático: ticket={} operacion={} estado={}",
                        ticket.getId(), op.getId(), ticket.getEstadoCotejo());
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
