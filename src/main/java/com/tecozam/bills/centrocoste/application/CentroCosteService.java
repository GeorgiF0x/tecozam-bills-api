package com.tecozam.bills.centrocoste.application;

import com.tecozam.bills.centrocoste.domain.CentroCoste;
import com.tecozam.bills.centrocoste.dto.CentroCosteDTO;
import com.tecozam.bills.centrocoste.dto.CreateCentroCosteRequest;
import com.tecozam.bills.centrocoste.dto.UpdateCentroCosteRequest;
import com.tecozam.bills.centrocoste.infrastructure.persistence.CentroCosteRepository;
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

    @Transactional(readOnly = true)
    public List<CentroCosteDTO> findAll(boolean soloActivos) {
        List<CentroCoste> centros = soloActivos
                ? centroCosteRepository.findByActivoTrue()
                : centroCosteRepository.findAll();
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
