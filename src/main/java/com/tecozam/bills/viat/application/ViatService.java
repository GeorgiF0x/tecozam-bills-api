package com.tecozam.bills.viat.application;

import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.viat.domain.Viat;
import com.tecozam.bills.viat.dto.CreateViatRequest;
import com.tecozam.bills.viat.dto.UpdateViatRequest;
import com.tecozam.bills.viat.dto.ViatDTO;
import com.tecozam.bills.viat.infrastructure.persistence.ViatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ViatService {

    private final ViatRepository viatRepository;
    private final PrestamoRepository prestamoRepository;

    @Transactional(readOnly = true)
    public List<ViatDTO> findAll(boolean soloActivos) {
        List<Viat> viats = soloActivos
                ? viatRepository.findByActivoTrueAndEliminadoEnIsNull()
                : viatRepository.findByEliminadoEnIsNull();
        return viats.stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ViatDTO findById(Long id) {
        Viat viat = viatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Viat", id));
        return toDTO(viat);
    }

    public ViatDTO create(CreateViatRequest request) {
        if (viatRepository.existsByCodigo(request.codigo())) {
            throw new DuplicateResourceException("Viat", "codigo", request.codigo());
        }

        Viat viat = Viat.builder()
                .codigo(request.codigo())
                .numeroSerie(request.numeroSerie())
                .descripcion(request.descripcion())
                .estado(EstadoRecurso.DISPONIBLE)
                .activo(true)
                .build();

        viat = viatRepository.save(viat);
        log.info("Viat creado: {} con codigo {}", viat.getId(), viat.getCodigo());

        return toDTO(viat);
    }

    public ViatDTO update(Long id, UpdateViatRequest request) {
        Viat viat = viatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Viat", id));

        if (request.numeroSerie() != null) {
            viat.setNumeroSerie(request.numeroSerie());
        }

        if (request.descripcion() != null) {
            viat.setDescripcion(request.descripcion());
        }

        if (request.estado() != null && !request.estado().isBlank()) {
            EstadoRecurso nuevoEstado = parseEstado(request.estado());
            viat.setEstado(nuevoEstado);
        }

        if (request.activo() != null) {
            viat.setActivo(request.activo());
        }

        viat = viatRepository.save(viat);
        log.info("Viat actualizado: {}", viat.getId());

        return toDTO(viat);
    }

    /**
     * Borrado logico (por defecto) o fisico (real=true) de un viat. Solo ADMIN
     * (verificado en el controller). El borrado real solo se permite si el
     * viat no tiene ningun historial asociado (prestamos); en caso contrario
     * se rechaza para no romper la trazabilidad de auditoria.
     */
    public void eliminar(Long id, boolean real) {
        Viat viat = viatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Viat", id));

        if (real) {
            eliminarFisicamente(viat);
            log.info("Viat {} eliminado (borrado fisico)", id);
        } else {
            viat.softDelete();
            viatRepository.save(viat);
            log.info("Viat {} eliminado (borrado logico)", id);
        }
    }

    /**
     * Borrado en masa (logico o real segun el parametro). Atomico: si algun id
     * no existe o no puede borrarse de forma real, no se elimina ninguno
     * (se propaga la excepcion y la transaccion revierte).
     */
    public void eliminarMasivo(List<Long> ids, boolean real) {
        for (Long id : ids) {
            Viat viat = viatRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Viat", id));
            if (real) {
                eliminarFisicamente(viat);
            } else {
                viat.softDelete();
                viatRepository.save(viat);
            }
        }
        log.info("Viats {} eliminados (borrado {} masivo)", ids, real ? "fisico" : "logico");
    }

    private void eliminarFisicamente(Viat viat) {
        if (prestamoRepository.existsByViatId(viat.getId())) {
            throw new BusinessException(
                    "No se puede eliminar permanentemente: este VIAT tiene historial asociado "
                            + "(prestamos). Usa el borrado logico en su lugar.");
        }
        viatRepository.delete(viat);
    }

    public void cambiarEstado(Long id, String estadoStr) {
        EstadoRecurso nuevoEstado = parseEstado(estadoStr);

        Viat viat = viatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Viat", id));

        viat.setEstado(nuevoEstado);
        viatRepository.save(viat);

        log.info("Estado del Viat {} cambiado a {}", id, nuevoEstado.name());
    }

    private EstadoRecurso parseEstado(String estadoStr) {
        try {
            return EstadoRecurso.valueOf(estadoStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "Estado inválido: " + estadoStr + ". Valores permitidos: DISPONIBLE, PRESTADO, BLOQUEADO, BAJA");
        }
    }

    private ViatDTO toDTO(Viat viat) {
        return new ViatDTO(
                viat.getId(),
                viat.getCodigo(),
                viat.getNumeroSerie(),
                viat.getDescripcion(),
                viat.getEstado().name(),
                viat.isActivo(),
                viat.getCreadoEn());
    }
}
