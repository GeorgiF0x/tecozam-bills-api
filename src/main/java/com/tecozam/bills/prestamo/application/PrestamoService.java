package com.tecozam.bills.prestamo.application;

import com.tecozam.bills.auth.domain.Usuario;
import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioRepository;
import com.tecozam.bills.centrocoste.domain.CentroCoste;
import com.tecozam.bills.centrocoste.infrastructure.persistence.CentroCosteRepository;
import com.tecozam.bills.prestamo.domain.Prestamo;
import com.tecozam.bills.prestamo.dto.CreateMiPrestamoRequest;
import com.tecozam.bills.prestamo.dto.CreatePrestamoRequest;
import com.tecozam.bills.prestamo.dto.DevolucionRequest;
import com.tecozam.bills.prestamo.dto.PrestamoDTO;
import com.tecozam.bills.prestamo.dto.RecursoDisponibleDTO;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import com.tecozam.bills.vehiculo.infrastructure.persistence.VehiculoRepository;
import com.tecozam.bills.viat.domain.Viat;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class PrestamoService {

    private final PrestamoRepository prestamoRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final CentroCosteRepository centroCosteRepository;
    private final TarjetaRepository tarjetaRepository;
    private final ViatRepository viatRepository;
    private final VehiculoRepository vehiculoRepository;
    private final UsuarioCampoRepository usuarioCampoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<PrestamoDTO> findAll(String estado) {
        List<Prestamo> prestamos = estado != null
                ? prestamoRepository.findByEstado(estado)
                : prestamoRepository.findAll();
        return prestamos.stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public List<PrestamoDTO> findByTrabajador(Long trabajadorId) {
        return prestamoRepository.findByTrabajadorId(trabajadorId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PrestamoDTO> findMisPrestamos(String username) {
        Trabajador trabajador = resolveTrabajador(username);
        if (trabajador == null) {
            log.warn("Usuario {} sin trabajador asociado, devolviendo lista vacía", username);
            return List.of();
        }
        return prestamoRepository.findByTrabajadorId(trabajador.getId()).stream()
                .map(this::toDTO)
                .toList();
    }

    private Trabajador resolveTrabajador(String username) {
        UsuarioCampo campo = usuarioCampoRepository.findByUsername(username).orElse(null);
        if (campo != null) {
            return campo.getTrabajador();
        }
        Usuario legacy = usuarioRepository.findByUsername(username).orElse(null);
        return legacy != null ? legacy.getTrabajador() : null;
    }

    @Transactional(readOnly = true)
    public List<RecursoDisponibleDTO> findRecursosDisponibles(String tipo) {
        if (tipo == null) {
            throw new BusinessException("tipoRecurso es obligatorio");
        }
        return switch (tipo.toUpperCase()) {
            case "TARJETA" -> tarjetaRepository.findByEstadoAndActivaTrue(EstadoRecurso.DISPONIBLE).stream()
                    .map(t -> {
                        String desc;
                        if (t.getAlias() != null && !t.getAlias().isBlank()) {
                            desc = t.getAlias();
                        } else if (t.getNumeroTarjeta() != null) {
                            String num = t.getNumeroTarjeta();
                            desc = "Tarjeta **** " + (num.length() >= 4 ? num.substring(num.length() - 4) : num);
                        } else {
                            desc = "Tarjeta #" + t.getId();
                        }
                        String detalle = null;
                        try {
                            if (t.getProveedor() != null) {
                                detalle = t.getProveedor().getNombre();
                            }
                        } catch (Exception ignore) {
                            // lazy init / proxy issue — no es crítico
                        }
                        return new RecursoDisponibleDTO(t.getId(), desc, detalle);
                    })
                    .toList();
            case "VIAT" -> viatRepository.findByEstadoAndActivoTrue(EstadoRecurso.DISPONIBLE).stream()
                    .map(v -> {
                        String desc = v.getCodigo() != null ? "Viat " + v.getCodigo() : "Viat #" + v.getId();
                        return new RecursoDisponibleDTO(v.getId(), desc, v.getDescripcion());
                    })
                    .toList();
            case "VEHICULO" -> vehiculoRepository.findByEstadoAndActivoTrue(EstadoRecurso.DISPONIBLE).stream()
                    .map(v -> {
                        String desc = v.getMatricula() != null ? v.getMatricula() : "Vehículo #" + v.getId();
                        String detalle = null;
                        String tipo2 = v.getTipo();
                        String descripcion = v.getDescripcion();
                        if (descripcion != null && !descripcion.isBlank()) {
                            detalle = descripcion;
                        } else if (tipo2 != null) {
                            detalle = tipo2;
                        }
                        return new RecursoDisponibleDTO(v.getId(), desc, detalle);
                    })
                    .toList();
            default -> throw new BusinessException("tipoRecurso no válido: " + tipo + ". Valores permitidos: TARJETA, VIAT, VEHICULO");
        };
    }

    public PrestamoDTO crearMiPrestamo(String username, CreateMiPrestamoRequest req) {
        Trabajador trabajador = resolveTrabajador(username);
        if (trabajador == null) {
            throw new BusinessException("El usuario no tiene un trabajador asociado", "usuario");
        }

        CentroCoste centroCoste = centroCosteRepository.findById(req.centroCosteId())
                .orElseThrow(() -> new ResourceNotFoundException("CentroCoste", req.centroCosteId()));

        Prestamo.PrestamoBuilder builder = Prestamo.builder()
                .tipoRecurso(req.tipoRecurso())
                .trabajador(trabajador)
                .centroCoste(centroCoste)
                .estado("ACTIVO")
                .fechaInicio(req.fechaInicio())
                .fechaFinPrevista(req.fechaFinPrevista())
                .observaciones(req.observaciones())
                .creadoPorCampo(true);

        switch (req.tipoRecurso().toUpperCase()) {
            case "TARJETA" -> {
                if (req.tarjetaId() == null) {
                    throw new BusinessException("tarjetaId es obligatorio para tipo TARJETA");
                }
                Tarjeta tarjeta = tarjetaRepository.findById(req.tarjetaId())
                        .orElseThrow(() -> new ResourceNotFoundException("Tarjeta", req.tarjetaId()));
                if (tarjeta.getEstado() != EstadoRecurso.DISPONIBLE) {
                    throw new BusinessException("La tarjeta no está disponible. Estado actual: " + tarjeta.getEstado());
                }
                builder.tarjeta(tarjeta);
                tarjeta.setEstado(EstadoRecurso.PRESTADO);
                tarjetaRepository.save(tarjeta);
            }
            case "VIAT" -> {
                if (req.viatId() == null) {
                    throw new BusinessException("viatId es obligatorio para tipo VIAT");
                }
                Viat viat = viatRepository.findById(req.viatId())
                        .orElseThrow(() -> new ResourceNotFoundException("Viat", req.viatId()));
                if (viat.getEstado() != EstadoRecurso.DISPONIBLE) {
                    throw new BusinessException("El viat no está disponible. Estado actual: " + viat.getEstado());
                }
                builder.viat(viat);
                viat.setEstado(EstadoRecurso.PRESTADO);
                viatRepository.save(viat);
            }
            case "VEHICULO" -> {
                if (req.vehiculoId() == null) {
                    throw new BusinessException("vehiculoId es obligatorio para tipo VEHICULO");
                }
                Vehiculo vehiculo = vehiculoRepository.findById(req.vehiculoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Vehiculo", req.vehiculoId()));
                if (vehiculo.getEstado() != EstadoRecurso.DISPONIBLE) {
                    throw new BusinessException("El vehículo no está disponible. Estado actual: " + vehiculo.getEstado());
                }
                builder.vehiculo(vehiculo);
                vehiculo.setEstado(EstadoRecurso.PRESTADO);
                vehiculoRepository.save(vehiculo);
            }
            default -> throw new BusinessException("tipoRecurso no válido: " + req.tipoRecurso());
        }

        Prestamo prestamo = prestamoRepository.save(builder.build());
        log.info("Préstamo self-service creado: id={} tipo={} trabajador={} username={}",
                prestamo.getId(), prestamo.getTipoRecurso(), trabajador.getId(), username);
        return toDTO(prestamo);
    }

    public PrestamoDTO miDevolucion(String username, Long prestamoId, String observaciones) {
        Trabajador trabajador = resolveTrabajador(username);
        if (trabajador == null) {
            throw new BusinessException("El usuario no tiene un trabajador asociado", "usuario");
        }

        Prestamo prestamo = prestamoRepository.findById(prestamoId)
                .orElseThrow(() -> new ResourceNotFoundException("Prestamo", prestamoId));

        if (prestamo.getTrabajador() == null || !prestamo.getTrabajador().getId().equals(trabajador.getId())) {
            throw new BusinessException("Solo puedes devolver tus propios préstamos", "prestamoId");
        }

        if (!"ACTIVO".equals(prestamo.getEstado()) && !"VENCIDO".equals(prestamo.getEstado())) {
            throw new BusinessException("Solo se pueden devolver préstamos en estado ACTIVO o VENCIDO. Estado actual: " + prestamo.getEstado());
        }

        prestamo.setEstado("DEVUELTO");
        prestamo.setFechaDevolucionReal(LocalDateTime.now());

        if (observaciones != null && !observaciones.isBlank()) {
            String obsActual = prestamo.getObservaciones();
            prestamo.setObservaciones(obsActual != null ? obsActual + " | " + observaciones : observaciones);
        }

        liberarRecurso(prestamo);
        Prestamo saved = prestamoRepository.save(prestamo);
        log.info("Devolución self-service: prestamoId={} username={}", prestamoId, username);
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public PrestamoDTO findById(Long id) {
        Prestamo prestamo = prestamoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prestamo", id));
        return toDTO(prestamo);
    }

    public PrestamoDTO create(CreatePrestamoRequest req) {
        Trabajador trabajador = trabajadorRepository.findById(req.trabajadorId())
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador", req.trabajadorId()));

        CentroCoste centroCoste = centroCosteRepository.findById(req.centroCosteId())
                .orElseThrow(() -> new ResourceNotFoundException("CentroCoste", req.centroCosteId()));

        Prestamo.PrestamoBuilder builder = Prestamo.builder()
                .tipoRecurso(req.tipoRecurso())
                .trabajador(trabajador)
                .centroCoste(centroCoste)
                .tipoPrestamo(req.tipoPrestamo())
                .estado("ACTIVO")
                .fechaInicio(req.fechaInicio())
                .fechaFinPrevista(req.fechaFinPrevista())
                .observaciones(req.observaciones());

        switch (req.tipoRecurso().toUpperCase()) {
            case "TARJETA" -> {
                if (req.tarjetaId() == null) {
                    throw new BusinessException("tarjetaId es obligatorio para tipo TARJETA");
                }
                Tarjeta tarjeta = tarjetaRepository.findById(req.tarjetaId())
                        .orElseThrow(() -> new ResourceNotFoundException("Tarjeta", req.tarjetaId()));
                if (tarjeta.getEstado() != EstadoRecurso.DISPONIBLE) {
                    throw new BusinessException("La tarjeta no está disponible. Estado actual: " + tarjeta.getEstado());
                }
                builder.tarjeta(tarjeta);
                tarjeta.setEstado(EstadoRecurso.PRESTADO);
                tarjetaRepository.save(tarjeta);
            }
            case "VIAT" -> {
                if (req.viatId() == null) {
                    throw new BusinessException("viatId es obligatorio para tipo VIAT");
                }
                Viat viat = viatRepository.findById(req.viatId())
                        .orElseThrow(() -> new ResourceNotFoundException("Viat", req.viatId()));
                if (viat.getEstado() != EstadoRecurso.DISPONIBLE) {
                    throw new BusinessException("El viat no está disponible. Estado actual: " + viat.getEstado());
                }
                builder.viat(viat);
                viat.setEstado(EstadoRecurso.PRESTADO);
                viatRepository.save(viat);
            }
            case "VEHICULO" -> {
                if (req.vehiculoId() == null) {
                    throw new BusinessException("vehiculoId es obligatorio para tipo VEHICULO");
                }
                Vehiculo vehiculo = vehiculoRepository.findById(req.vehiculoId())
                        .orElseThrow(() -> new ResourceNotFoundException("Vehiculo", req.vehiculoId()));
                if (vehiculo.getEstado() != EstadoRecurso.DISPONIBLE) {
                    throw new BusinessException("El vehículo no está disponible. Estado actual: " + vehiculo.getEstado());
                }
                builder.vehiculo(vehiculo);
                vehiculo.setEstado(EstadoRecurso.PRESTADO);
                vehiculoRepository.save(vehiculo);
            }
            default -> throw new BusinessException("tipoRecurso no válido: " + req.tipoRecurso() + ". Valores permitidos: TARJETA, VIAT, VEHICULO");
        }

        Prestamo prestamo = prestamoRepository.save(builder.build());
        log.info("Préstamo creado: id={}, tipo={}, trabajador={}", prestamo.getId(), prestamo.getTipoRecurso(), trabajador.getId());
        return toDTO(prestamo);
    }

    public PrestamoDTO registrarDevolucion(Long id, DevolucionRequest req) {
        Prestamo prestamo = prestamoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prestamo", id));

        if (!"ACTIVO".equals(prestamo.getEstado()) && !"VENCIDO".equals(prestamo.getEstado())) {
            throw new BusinessException("Solo se pueden devolver préstamos en estado ACTIVO o VENCIDO. Estado actual: " + prestamo.getEstado());
        }

        prestamo.setEstado("DEVUELTO");
        prestamo.setFechaDevolucionReal(LocalDateTime.now());

        if (req != null && req.observaciones() != null && !req.observaciones().isBlank()) {
            String obsActual = prestamo.getObservaciones();
            prestamo.setObservaciones(obsActual != null ? obsActual + " | " + req.observaciones() : req.observaciones());
        }

        liberarRecurso(prestamo);
        Prestamo saved = prestamoRepository.save(prestamo);
        log.info("Devolución registrada: prestamoId={}", id);
        return toDTO(saved);
    }

    public void cancelar(Long id) {
        Prestamo prestamo = prestamoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prestamo", id));

        if ("DEVUELTO".equals(prestamo.getEstado()) || "CANCELADO".equals(prestamo.getEstado())) {
            throw new BusinessException("No se puede cancelar un préstamo ya " + prestamo.getEstado().toLowerCase());
        }

        prestamo.setEstado("CANCELADO");
        liberarRecurso(prestamo);
        prestamoRepository.save(prestamo);
        log.info("Préstamo cancelado: id={}", id);
    }

    private void liberarRecurso(Prestamo prestamo) {
        switch (prestamo.getTipoRecurso().toUpperCase()) {
            case "TARJETA" -> {
                if (prestamo.getTarjeta() != null) {
                    prestamo.getTarjeta().setEstado(EstadoRecurso.DISPONIBLE);
                    tarjetaRepository.save(prestamo.getTarjeta());
                }
            }
            case "VIAT" -> {
                if (prestamo.getViat() != null) {
                    prestamo.getViat().setEstado(EstadoRecurso.DISPONIBLE);
                    viatRepository.save(prestamo.getViat());
                }
            }
            case "VEHICULO" -> {
                if (prestamo.getVehiculo() != null) {
                    prestamo.getVehiculo().setEstado(EstadoRecurso.DISPONIBLE);
                    vehiculoRepository.save(prestamo.getVehiculo());
                }
            }
        }
    }

    private PrestamoDTO toDTO(Prestamo p) {
        String recursoDescripcion = switch (p.getTipoRecurso().toUpperCase()) {
            case "TARJETA" -> p.getTarjeta() != null ? "Tarjeta " + p.getTarjeta().getNumeroTarjeta() : "Tarjeta";
            case "VIAT" -> p.getViat() != null ? "Viat " + p.getViat().getCodigo() : "Viat";
            case "VEHICULO" -> p.getVehiculo() != null ? "Veh. " + p.getVehiculo().getMatricula() : "Vehículo";
            default -> p.getTipoRecurso();
        };

        return new PrestamoDTO(
                p.getId(),
                p.getTipoRecurso(),
                p.getTarjeta() != null ? p.getTarjeta().getId() : null,
                p.getViat() != null ? p.getViat().getId() : null,
                p.getVehiculo() != null ? p.getVehiculo().getId() : null,
                recursoDescripcion,
                p.getTrabajador() != null ? p.getTrabajador().getId() : null,
                p.getTrabajador() != null ? p.getTrabajador().getNombre() + " " + p.getTrabajador().getApellidos() : null,
                p.getCentroCoste() != null ? p.getCentroCoste().getId() : null,
                p.getCentroCoste() != null ? p.getCentroCoste().getNombre() : null,
                p.getTipoPrestamo(),
                p.getEstado(),
                p.getFechaInicio(),
                p.getFechaFinPrevista(),
                p.getFechaDevolucionReal(),
                p.getObservaciones(),
                p.isCreadoPorCampo(),
                p.getCreadoEn()
        );
    }
}
