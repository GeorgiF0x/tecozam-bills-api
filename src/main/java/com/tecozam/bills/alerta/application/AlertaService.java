package com.tecozam.bills.alerta.application;

import com.tecozam.bills.alerta.domain.AlertaPrestamo;
import com.tecozam.bills.alerta.dto.AlertaDTO;
import com.tecozam.bills.alerta.infrastructure.persistence.AlertaPrestamoRepository;
import com.tecozam.bills.prestamo.domain.Prestamo;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
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
public class AlertaService {

    private final AlertaPrestamoRepository alertaPrestamoRepository;
    private final PrestamoRepository prestamoRepository;

    @Transactional(readOnly = true)
    public List<AlertaDTO> findPendientes() {
        return alertaPrestamoRepository.findByLeidaFalseOrderByFechaAlertaDesc().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countPendientes() {
        return alertaPrestamoRepository.countByLeidaFalse();
    }

    public void marcarLeida(Long id) {
        AlertaPrestamo alerta = alertaPrestamoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AlertaPrestamo", id));
        alerta.setLeida(true);
        alertaPrestamoRepository.save(alerta);
        log.info("Alerta marcada como leída: id={}", id);
    }

    public void marcarTodasLeidas() {
        List<AlertaPrestamo> pendientes = alertaPrestamoRepository.findByLeidaFalseOrderByFechaAlertaDesc();
        pendientes.forEach(a -> a.setLeida(true));
        alertaPrestamoRepository.saveAll(pendientes);
        log.info("Marcadas {} alertas como leídas", pendientes.size());
    }

    private AlertaDTO toDTO(AlertaPrestamo a) {
        String recursoDescripcion = null;
        String trabajadorNombre = null;

        Prestamo prestamo = a.getPrestamo();
        if (prestamo != null) {
            recursoDescripcion = switch (prestamo.getTipoRecurso().toUpperCase()) {
                case "TARJETA" -> prestamo.getTarjeta() != null
                        ? "Tarjeta " + prestamo.getTarjeta().getNumeroTarjeta()
                        : "Tarjeta";
                case "VIAT" -> prestamo.getViat() != null
                        ? "Viat " + prestamo.getViat().getCodigo()
                        : "Viat";
                case "VEHICULO" -> prestamo.getVehiculo() != null
                        ? "Veh. " + prestamo.getVehiculo().getMatricula()
                        : "Vehículo";
                default -> prestamo.getTipoRecurso();
            };

            if (prestamo.getTrabajador() != null) {
                trabajadorNombre = prestamo.getTrabajador().getNombre()
                        + " " + prestamo.getTrabajador().getApellidos();
            }
        }

        return new AlertaDTO(
                a.getId(),
                prestamo != null ? prestamo.getId() : null,
                a.getTipoAlerta(),
                a.getFechaAlerta(),
                a.getMensaje(),
                a.isEmailEnviado(),
                a.isLeida(),
                a.getCreadoEn(),
                recursoDescripcion,
                trabajadorNombre
        );
    }
}
