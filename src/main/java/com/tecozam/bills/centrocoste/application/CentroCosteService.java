package com.tecozam.bills.centrocoste.application;

import com.tecozam.bills.centrocoste.domain.CentroCoste;
import com.tecozam.bills.centrocoste.dto.CentroCosteDTO;
import com.tecozam.bills.centrocoste.dto.CreateCentroCosteRequest;
import com.tecozam.bills.centrocoste.dto.UpdateCentroCosteRequest;
import com.tecozam.bills.centrocoste.infrastructure.persistence.CentroCosteRepository;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CentroCosteService {

    private final CentroCosteRepository centroCosteRepository;
    private final PrestamoRepository prestamoRepository;

    @Transactional(readOnly = true)
    public List<CentroCosteDTO> findAll(boolean soloActivos) {
        List<CentroCoste> centros = soloActivos
                ? centroCosteRepository.findByActivoTrueAndEliminadoEnIsNull()
                : centroCosteRepository.findByEliminadoEnIsNull();
        return centros.stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public CentroCosteDTO findById(Long id) {
        CentroCoste centroCoste = centroCosteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CentroCoste", id));
        return toDTO(centroCoste);
    }

    public CentroCosteDTO create(CreateCentroCosteRequest request) {
        if (centroCosteRepository.existsByCodigo(request.codigo())) {
            throw new DuplicateResourceException("CentroCoste", "codigo", request.codigo());
        }

        CentroCoste centroCoste = CentroCoste.builder()
                .codigo(request.codigo())
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .activo(true)
                .build();

        centroCoste = centroCosteRepository.save(centroCoste);
        log.info("CentroCoste creado: {} - {} (id={})", centroCoste.getCodigo(),
                centroCoste.getNombre(), centroCoste.getId());

        return toDTO(centroCoste);
    }

    public CentroCosteDTO update(Long id, UpdateCentroCosteRequest request) {
        CentroCoste centroCoste = centroCosteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CentroCoste", id));

        if (request.nombre() != null && !request.nombre().isBlank()) {
            centroCoste.setNombre(request.nombre());
        }

        if (request.descripcion() != null) {
            centroCoste.setDescripcion(request.descripcion());
        }

        if (request.activo() != null) {
            centroCoste.setActivo(request.activo());
        }

        centroCoste = centroCosteRepository.save(centroCoste);
        log.info("CentroCoste actualizado: {} (id={})", centroCoste.getCodigo(), centroCoste.getId());

        return toDTO(centroCoste);
    }

    /**
     * Borrado logico (por defecto) o fisico (real=true) de un centro de coste.
     * Solo ADMIN (verificado en el controller). El borrado real solo se
     * permite si el centro de coste no tiene ningun historial asociado
     * (prestamos); en caso contrario se rechaza para no romper la
     * trazabilidad de auditoria.
     */
    public void eliminar(Long id, boolean real) {
        CentroCoste centroCoste = centroCosteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CentroCoste", id));

        if (real) {
            eliminarFisicamente(centroCoste);
            log.info("CentroCoste {} eliminado (borrado fisico)", id);
        } else {
            centroCoste.softDelete();
            centroCosteRepository.save(centroCoste);
            log.info("CentroCoste {} eliminado (borrado logico)", id);
        }
    }

    /**
     * Borrado en masa (logico o real segun el parametro). Atomico: si algun
     * id no existe o no puede borrarse de forma real, no se elimina ninguno
     * (se propaga la excepcion y la transaccion revierte).
     */
    public void eliminarMasivo(List<Long> ids, boolean real) {
        for (Long id : ids) {
            CentroCoste centroCoste = centroCosteRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("CentroCoste", id));
            if (real) {
                eliminarFisicamente(centroCoste);
            } else {
                centroCoste.softDelete();
                centroCosteRepository.save(centroCoste);
            }
        }
        log.info("Centros de coste {} eliminados (borrado {} masivo)", ids, real ? "fisico" : "logico");
    }

    private void eliminarFisicamente(CentroCoste centroCoste) {
        if (prestamoRepository.existsByCentroCosteId(centroCoste.getId())) {
            throw new BusinessException(
                    "No se puede eliminar permanentemente: este centro de coste tiene historial asociado "
                            + "(prestamos). Usa el borrado logico en su lugar.");
        }
        centroCosteRepository.delete(centroCoste);
    }

    public void toggleActivo(Long id) {
        CentroCoste centroCoste = centroCosteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CentroCoste", id));

        centroCoste.setActivo(!centroCoste.isActivo());
        centroCosteRepository.save(centroCoste);

        log.info("CentroCoste {} (id={}): activo={}", centroCoste.getCodigo(), id, centroCoste.isActivo());
    }

    private CentroCosteDTO toDTO(CentroCoste centroCoste) {
        return new CentroCosteDTO(
                centroCoste.getId(),
                centroCoste.getCodigo(),
                centroCoste.getNombre(),
                centroCoste.getDescripcion(),
                centroCoste.isActivo(),
                centroCoste.getCreadoEn()
        );
    }
}
