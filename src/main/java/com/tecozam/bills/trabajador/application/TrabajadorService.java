package com.tecozam.bills.trabajador.application;

import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
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

    @Transactional(readOnly = true)
    public List<TrabajadorDTO> findAll(boolean soloActivos) {
        List<Trabajador> trabajadores = soloActivos
                ? trabajadorRepository.findByActivoTrue()
                : trabajadorRepository.findAll();

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

    private TrabajadorDTO toDTO(Trabajador trabajador) {
        return new TrabajadorDTO(
                trabajador.getId(),
                trabajador.getNombre(),
                trabajador.getApellidos(),
                trabajador.getEmail(),
                trabajador.isActivo(),
                trabajador.getCreadoEn()
        );
    }
}
