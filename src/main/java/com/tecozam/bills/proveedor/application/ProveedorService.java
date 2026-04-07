package com.tecozam.bills.proveedor.application;

import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.dto.CreateProveedorRequest;
import com.tecozam.bills.proveedor.dto.ProveedorDTO;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
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
public class ProveedorService {

    private final ProveedorRepository proveedorRepository;

    @Transactional(readOnly = true)
    public List<ProveedorDTO> findAll() {
        return proveedorRepository.findByActivoTrue().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProveedorDTO> findTodos() {
        return proveedorRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProveedorDTO findById(Long id) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));
        return toDTO(proveedor);
    }

    public ProveedorDTO create(CreateProveedorRequest request) {
        if (proveedorRepository.existsByCodigo(request.codigo())) {
            throw new DuplicateResourceException("Proveedor", "codigo", request.codigo());
        }

        Proveedor proveedor = Proveedor.builder()
                .codigo(request.codigo())
                .nombre(request.nombre())
                .nif(request.nif())
                .activo(true)
                .build();

        proveedor = proveedorRepository.save(proveedor);
        log.info("Proveedor creado: {} - {} (id={})", proveedor.getCodigo(),
                proveedor.getNombre(), proveedor.getId());

        return toDTO(proveedor);
    }

    public ProveedorDTO update(Long id, CreateProveedorRequest request) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));

        // Valida unicidad del código si cambia
        if (!proveedor.getCodigo().equals(request.codigo())
                && proveedorRepository.existsByCodigo(request.codigo())) {
            throw new DuplicateResourceException("Proveedor", "codigo", request.codigo());
        }

        proveedor.setCodigo(request.codigo());
        proveedor.setNombre(request.nombre());
        proveedor.setNif(request.nif());

        proveedor = proveedorRepository.save(proveedor);
        log.info("Proveedor actualizado: {} (id={})", proveedor.getCodigo(), proveedor.getId());

        return toDTO(proveedor);
    }

    public void toggleActivo(Long id) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));

        proveedor.setActivo(!proveedor.isActivo());
        proveedorRepository.save(proveedor);

        log.info("Proveedor {} (id={}): activo={}", proveedor.getCodigo(), id, proveedor.isActivo());
    }

    private ProveedorDTO toDTO(Proveedor proveedor) {
        return new ProveedorDTO(
                proveedor.getId(),
                proveedor.getCodigo(),
                proveedor.getNombre(),
                proveedor.getNif(),
                proveedor.isActivo()
        );
    }
}
