package com.tecozam.bills.proveedor.application;

import com.tecozam.bills.factura.infrastructure.persistence.FacturaRepository;
import com.tecozam.bills.proveedor.domain.Proveedor;
import com.tecozam.bills.proveedor.dto.CreateProveedorRequest;
import com.tecozam.bills.proveedor.dto.ProveedorDTO;
import com.tecozam.bills.proveedor.infrastructure.persistence.ProveedorRepository;
import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.DuplicateResourceException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.tarifa.infrastructure.persistence.TarifaRepository;
import com.tecozam.bills.tarjeta.infrastructure.persistence.TarjetaRepository;
import com.tecozam.bills.ticket.infrastructure.persistence.TicketRepository;
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
    private final TarifaRepository tarifaRepository;
    private final FacturaRepository facturaRepository;
    private final TicketRepository ticketRepository;
    private final TarjetaRepository tarjetaRepository;

    @Transactional(readOnly = true)
    public List<ProveedorDTO> findAll() {
        return proveedorRepository.findByActivoTrueAndEliminadoEnIsNull().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProveedorDTO> findTodos() {
        return proveedorRepository.findByEliminadoEnIsNull().stream()
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

    /**
     * Borrado logico (por defecto) o fisico (real=true) de un proveedor. Solo
     * ADMIN (verificado en el controller). El borrado real solo se permite si
     * el proveedor no tiene ningun historial asociado (tarifas, facturas,
     * tickets o tarjetas); en caso contrario se rechaza para no romper la
     * trazabilidad de auditoria.
     */
    public void eliminar(Long id, boolean real) {
        Proveedor proveedor = proveedorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));

        if (real) {
            eliminarFisicamente(proveedor);
            log.info("Proveedor {} eliminado (borrado fisico)", id);
        } else {
            proveedor.softDelete();
            proveedorRepository.save(proveedor);
            log.info("Proveedor {} eliminado (borrado logico)", id);
        }
    }

    /**
     * Borrado en masa (logico o real segun el parametro). Atomico: si algun id
     * no existe o no puede borrarse de forma real, no se elimina ninguno
     * (se propaga la excepcion y la transaccion revierte).
     */
    public void eliminarMasivo(List<Long> ids, boolean real) {
        for (Long id : ids) {
            Proveedor proveedor = proveedorRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Proveedor", id));
            if (real) {
                eliminarFisicamente(proveedor);
            } else {
                proveedor.softDelete();
                proveedorRepository.save(proveedor);
            }
        }
        log.info("Proveedores {} eliminados (borrado {} masivo)", ids, real ? "fisico" : "logico");
    }

    private void eliminarFisicamente(Proveedor proveedor) {
        if (tieneHistorial(proveedor.getId())) {
            throw new BusinessException(
                    "No se puede eliminar permanentemente: este proveedor tiene historial asociado "
                            + "(tarifas, facturas, tickets o tarjetas). Usa el borrado logico en su lugar.");
        }
        proveedorRepository.delete(proveedor);
    }

    private boolean tieneHistorial(Long proveedorId) {
        return tarifaRepository.existsByProveedorId(proveedorId)
                || facturaRepository.existsByProveedorId(proveedorId)
                || ticketRepository.existsByProveedorId(proveedorId)
                || tarjetaRepository.existsByProveedorId(proveedorId);
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
