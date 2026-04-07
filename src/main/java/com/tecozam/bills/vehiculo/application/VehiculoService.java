package com.tecozam.bills.vehiculo.application;

import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import com.tecozam.bills.vehiculo.dto.CreateVehiculoRequest;
import com.tecozam.bills.vehiculo.dto.UpdateVehiculoRequest;
import com.tecozam.bills.vehiculo.dto.VehiculoDTO;
import com.tecozam.bills.vehiculo.infrastructure.persistence.VehiculoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class VehiculoService {

    private final VehiculoRepository vehiculoRepository;

    @Transactional(readOnly = true)
    public List<VehiculoDTO> findAll(boolean soloActivos) {
        List<Vehiculo> vehiculos = soloActivos
                ? vehiculoRepository.findByActivoTrue()
                : vehiculoRepository.findAll();
        return vehiculos.stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public VehiculoDTO findById(Long id) {
        Vehiculo vehiculo = vehiculoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehiculo", id));
        return toDTO(vehiculo);
    }

    public VehiculoDTO create(CreateVehiculoRequest request) {
        if (vehiculoRepository.existsByMatricula(request.matricula())) {
            throw new DuplicateResourceException("Vehiculo", "matricula", request.matricula());
        }

        Vehiculo vehiculo = Vehiculo.builder()
                .matricula(request.matricula())
                .tipo(request.tipo())
                .descripcion(request.descripcion())
                .estado(EstadoRecurso.DISPONIBLE)
                .activo(true)
                .build();

        vehiculo = vehiculoRepository.save(vehiculo);
        log.info("Vehiculo creado: {} (id={})", vehiculo.getMatricula(), vehiculo.getId());

        return toDTO(vehiculo);
    }

    public VehiculoDTO update(Long id, UpdateVehiculoRequest request) {
        Vehiculo vehiculo = vehiculoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehiculo", id));

        if (request.descripcion() != null) {
            vehiculo.setDescripcion(request.descripcion());
        }

        if (request.estado() != null && !request.estado().isBlank()) {
            EstadoRecurso nuevoEstado = parseEstado(request.estado());
            vehiculo.setEstado(nuevoEstado);
        }

        if (request.activo() != null) {
            vehiculo.setActivo(request.activo());
        }

        vehiculo = vehiculoRepository.save(vehiculo);
        log.info("Vehiculo actualizado: {} (id={})", vehiculo.getMatricula(), vehiculo.getId());

        return toDTO(vehiculo);
    }

    public void cambiarEstado(Long id, String estadoStr) {
        EstadoRecurso nuevoEstado = parseEstado(estadoStr);

        Vehiculo vehiculo = vehiculoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vehiculo", id));

        vehiculo.setEstado(nuevoEstado);
        vehiculoRepository.save(vehiculo);

        log.info("Estado del Vehiculo {} cambiado a {}", id, nuevoEstado.name());
    }

    private EstadoRecurso parseEstado(String estadoStr) {
        try {
            return EstadoRecurso.valueOf(estadoStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(
                    "Estado inválido: " + estadoStr + ". Valores permitidos: DISPONIBLE, PRESTADO, BLOQUEADO, BAJA");
        }
    }

    private VehiculoDTO toDTO(Vehiculo vehiculo) {
        return new VehiculoDTO(
                vehiculo.getId(),
                vehiculo.getMatricula(),
                vehiculo.getTipo(),
                vehiculo.getDescripcion(),
                vehiculo.getEstado().name(),
                vehiculo.isActivo(),
                vehiculo.getCreadoEn()
        );
    }
}
