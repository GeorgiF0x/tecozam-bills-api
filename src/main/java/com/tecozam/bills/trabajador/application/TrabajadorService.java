package com.tecozam.bills.trabajador.application;

import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioOficinaRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioRepository;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
import com.tecozam.bills.trabajador.domain.OrigenTrabajador;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.dto.CreateTrabajadorRequest;
import com.tecozam.bills.trabajador.dto.TrabajadorDTO;
import com.tecozam.bills.trabajador.dto.UpdateTrabajadorRequest;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TrabajadorService {

    private final TrabajadorRepository trabajadorRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioCampoRepository usuarioCampoRepository;
    private final UsuarioOficinaRepository usuarioOficinaRepository;
    private final PrestamoRepository prestamoRepository;
    private final TarjetaAsignacionRepository tarjetaAsignacionRepository;
    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public List<TrabajadorDTO> findAll(boolean soloActivos) {
        List<Trabajador> trabajadores = soloActivos
                ? trabajadorRepository.findByActivoTrueAndEliminadoEnIsNull()
                : trabajadorRepository.findByEliminadoEnIsNull();

        return trabajadores.stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public TrabajadorDTO findById(Long id) {
        Trabajador trabajador = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador", id));
        return toDTO(trabajador);
    }

    public TrabajadorDTO create(CreateTrabajadorRequest request) {
        if (request.email() != null && !request.email().isBlank()) {
            if (trabajadorRepository.existsByEmail(request.email())) {
                throw new DuplicateResourceException("Trabajador", "email", request.email());
            }
        }

        Trabajador trabajador = Trabajador.builder()
                .nombre(request.nombre())
                .apellidos(request.apellidos())
                .email(request.email())
                .dniNie(request.dniNie())
                .activo(true)
                .origen(OrigenTrabajador.OFICINA)
                .build();

        trabajador = trabajadorRepository.save(trabajador);
        log.info("Trabajador creado: {} {} (id={})",
                trabajador.getNombre(), trabajador.getApellidos(), trabajador.getId());

        return toDTO(trabajador);
    }

    public TrabajadorDTO update(Long id, UpdateTrabajadorRequest request) {
        Trabajador trabajador = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador", id));

        if (request.nombre() != null && !request.nombre().isBlank()) {
            trabajador.setNombre(request.nombre());
        }

        if (request.apellidos() != null && !request.apellidos().isBlank()) {
            trabajador.setApellidos(request.apellidos());
        }

        if (request.email() != null) {
            if (!request.email().isBlank() && !request.email().equals(trabajador.getEmail())) {
                if (trabajadorRepository.existsByEmail(request.email())) {
                    throw new DuplicateResourceException("Trabajador", "email", request.email());
                }
            }
            trabajador.setEmail(request.email().isBlank() ? null : request.email());
        }

        if (request.activo() != null) {
            trabajador.setActivo(request.activo());
        }

        trabajador = trabajadorRepository.save(trabajador);
        log.info("Trabajador actualizado: {} {} (id={})",
                trabajador.getNombre(), trabajador.getApellidos(), trabajador.getId());

        return toDTO(trabajador);
    }

    public void toggleActivo(Long id) {
        Trabajador trabajador = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador", id));

        trabajador.setActivo(!trabajador.isActivo());
        trabajadorRepository.save(trabajador);

        log.info("Trabajador {}: {} {} (id={})",
                trabajador.isActivo() ? "activado" : "desactivado",
                trabajador.getNombre(), trabajador.getApellidos(), id);
    }

    /**
     * Borrado logico (por defecto) o fisico (real=true) de un trabajador. Solo
     * ADMIN (verificado en el controller). El borrado real solo se permite si
     * el trabajador no tiene ningun historial asociado (usuarios, asignaciones
     * de tarjeta, tickets o prestamos); en caso contrario se rechaza para no
     * romper la trazabilidad de auditoria.
     */
    public void eliminar(Long id, boolean real) {
        Trabajador trabajador = trabajadorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador", id));

        if (real) {
            eliminarFisicamente(trabajador);
            log.info("Trabajador {} eliminado (borrado fisico)", id);
        } else {
            trabajador.softDelete();
            trabajadorRepository.save(trabajador);
            log.info("Trabajador {} eliminado (borrado logico)", id);
        }
    }

    /**
     * Borrado en masa (logico o real segun el parametro). Atomico: si algun id
     * no existe o no puede borrarse de forma real, no se elimina ninguno
     * (se propaga la excepcion y la transaccion revierte).
     */
    public void eliminarMasivo(List<Long> ids, boolean real) {
        for (Long id : ids) {
            Trabajador trabajador = trabajadorRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Trabajador", id));
            if (real) {
                eliminarFisicamente(trabajador);
            } else {
                trabajador.softDelete();
                trabajadorRepository.save(trabajador);
            }
        }
        log.info("Trabajadores {} eliminados (borrado {} masivo)", ids, real ? "fisico" : "logico");
    }

    private void eliminarFisicamente(Trabajador trabajador) {
        if (tieneHistorial(trabajador.getId())) {
            throw new BusinessException(
                    "No se puede eliminar permanentemente: este trabajador tiene historial asociado "
                            + "(usuarios, asignaciones de tarjeta, tickets o prestamos). Usa el borrado logico en su lugar.");
        }
        trabajadorRepository.delete(trabajador);
    }

    private boolean tieneHistorial(Long trabajadorId) {
        return usuarioRepository.existsByTrabajadorId(trabajadorId)
                || usuarioCampoRepository.existsByTrabajadorId(trabajadorId)
                || usuarioOficinaRepository.existsByTrabajadorId(trabajadorId)
                || prestamoRepository.existsByTrabajadorId(trabajadorId)
                || tarjetaAsignacionRepository.existsByTrabajadorId(trabajadorId)
                || ticketRepository.existsByTrabajadorId(trabajadorId);
    }

    private TrabajadorDTO toDTO(Trabajador trabajador) {
        return new TrabajadorDTO(
                trabajador.getId(),
                trabajador.getNombre(),
                trabajador.getApellidos(),
                trabajador.getEmail(),
                trabajador.getDniNie(),
                trabajador.isActivo(),
                trabajador.getOrigen() != null ? trabajador.getOrigen().name() : null,
                trabajador.getCreadoEn()
        );
    }
}
