package com.tecozam.bills.tarjeta.application;

import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.domain.enums.EstadoRecurso;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.tarjeta.domain.Tarjeta;
import com.tecozam.bills.tarjeta.domain.TarjetaAsignacion;
import com.tecozam.bills.tarjeta.dto.AsignarTarjetaRequest;
import com.tecozam.bills.tarjeta.dto.CreateTarjetaRequest;
import com.tecozam.bills.tarjeta.dto.TarjetaAsignacionDTO;
import com.tecozam.bills.tarjeta.dto.TarjetaDTO;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaAsignacionRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import com.tecozam.bills.vehiculo.domain.Vehiculo;
import com.tecozam.bills.vehiculo.infrastructure.persistence.VehiculoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TarjetaService {

    private final TarjetaRepository tarjetaRepository;
    private final TarjetaAsignacionRepository tarjetaAsignacionRepository;
    private final ProveedorRepository proveedorRepository;
    private final TrabajadorRepository trabajadorRepository;
    private final VehiculoRepository vehiculoRepository;

    @Transactional(readOnly = true)
    public List<TarjetaDTO> findAll(boolean soloActivas) {
        List<Tarjeta> tarjetas = soloActivas
                ? tarjetaRepository.findByActivaTrue()
                : tarjetaRepository.findAll();
        return tarjetas.stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public TarjetaDTO findById(Long id) {
        Tarjeta tarjeta = tarjetaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta", id));
        return toDTO(tarjeta);
    }

    public TarjetaDTO create(CreateTarjetaRequest request) {
        if (tarjetaRepository.existsByNumeroTarjeta(request.numeroTarjeta())) {
            throw new DuplicateResourceException("Tarjeta", "numeroTarjeta", request.numeroTarjeta());
        }

        Proveedor proveedor = proveedorRepository.findById(request.proveedorId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", request.proveedorId()));

        Tarjeta tarjeta = Tarjeta.builder()
                .numeroTarjeta(request.numeroTarjeta())
                .alias(request.alias())
                .proveedor(proveedor)
                .estado(EstadoRecurso.DISPONIBLE)
                .activa(true)
                .build();

        tarjeta = tarjetaRepository.save(tarjeta);
        log.info("Tarjeta creada: {} (id={})", tarjeta.getNumeroTarjeta(), tarjeta.getId());

        return toDTO(tarjeta);
    }

    public TarjetaDTO asignar(Long tarjetaId, AsignarTarjetaRequest request) {
        Tarjeta tarjeta = tarjetaRepository.findById(tarjetaId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta", tarjetaId));

        // Cierra asignación activa anterior si existe
        tarjetaAsignacionRepository.findByTarjetaIdAndFechaHastaIsNull(tarjetaId)
                .ifPresent(asignacion -> {
                    asignacion.setFechaHasta(LocalDate.now());
                    tarjetaAsignacionRepository.save(asignacion);
                });

        Trabajador trabajador = trabajadorRepository.findById(request.trabajadorId())
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador", request.trabajadorId()));

        Vehiculo vehiculo = null;
        if (request.vehiculoId() != null) {
            vehiculo = vehiculoRepository.findById(request.vehiculoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Vehiculo", request.vehiculoId()));
        }

        TarjetaAsignacion nuevaAsignacion = TarjetaAsignacion.builder()
                .tarjeta(tarjeta)
                .trabajador(trabajador)
                .vehiculo(vehiculo)
                .fechaDesde(request.fechaDesde())
                .fechaHasta(request.fechaHasta())
                .build();

        tarjetaAsignacionRepository.save(nuevaAsignacion);

        tarjeta.setEstado(EstadoRecurso.PRESTADO);
        tarjeta = tarjetaRepository.save(tarjeta);

        log.info("Tarjeta {} asignada al trabajador {} (id={})", tarjeta.getNumeroTarjeta(),
                trabajador.getNombre(), tarjetaId);

        return toDTO(tarjeta);
    }

    public TarjetaDTO devolver(Long tarjetaId) {
        Tarjeta tarjeta = tarjetaRepository.findById(tarjetaId)
                .orElseThrow(() -> new ResourceNotFoundException("Tarjeta", tarjetaId));

        // Cierra asignación activa
        tarjetaAsignacionRepository.findByTarjetaIdAndFechaHastaIsNull(tarjetaId)
                .ifPresent(asignacion -> {
                    asignacion.setFechaHasta(LocalDate.now());
                    tarjetaAsignacionRepository.save(asignacion);
                });

        tarjeta.setEstado(EstadoRecurso.DISPONIBLE);
        tarjeta = tarjetaRepository.save(tarjeta);

        log.info("Tarjeta {} devuelta (id={})", tarjeta.getNumeroTarjeta(), tarjetaId);

        return toDTO(tarjeta);
    }

    @Transactional(readOnly = true)
    public List<TarjetaAsignacionDTO> getHistorial(Long tarjetaId) {
        if (!tarjetaRepository.existsById(tarjetaId)) {
            throw new ResourceNotFoundException("Tarjeta", tarjetaId);
        }
        return tarjetaAsignacionRepository.findByTarjetaIdOrderByFechaDesdeDesc(tarjetaId)
                .stream()
                .map(this::toAsignacionDTO)
                .toList();
    }

    private TarjetaDTO toDTO(Tarjeta tarjeta) {
        TarjetaAsignacionDTO asignacionActual = tarjetaAsignacionRepository
                .findByTarjetaIdAndFechaHastaIsNull(tarjeta.getId())
                .map(this::toAsignacionDTO)
                .orElse(null);

        return new TarjetaDTO(
                tarjeta.getId(),
                tarjeta.getNumeroTarjeta(),
                tarjeta.getAlias(),
                tarjeta.getProveedor().getId(),
                tarjeta.getProveedor().getNombre(),
                tarjeta.getEstado().name(),
                tarjeta.isActiva(),
                tarjeta.getCreadoEn(),
                asignacionActual
        );
    }

    private TarjetaAsignacionDTO toAsignacionDTO(TarjetaAsignacion asignacion) {
        return new TarjetaAsignacionDTO(
                asignacion.getId(),
                asignacion.getTarjeta().getId(),
                asignacion.getTrabajador().getId(),
                asignacion.getTrabajador().getNombre() + " " + asignacion.getTrabajador().getApellidos(),
                asignacion.getVehiculo() != null ? asignacion.getVehiculo().getId() : null,
                asignacion.getVehiculo() != null ? asignacion.getVehiculo().getMatricula() : null,
                asignacion.getFechaDesde(),
                asignacion.getFechaHasta()
        );
    }
}
