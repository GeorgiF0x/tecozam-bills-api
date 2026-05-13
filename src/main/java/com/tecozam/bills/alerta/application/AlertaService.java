package com.tecozam.bills.alerta.application;

import com.tecozam.bills.alerta.domain.AlertaPrestamo;
import com.tecozam.bills.alerta.dto.AlertaDTO;
import com.tecozam.bills.alerta.infrastructure.persistence.AlertaPrestamoRepository;
import com.tecozam.bills.auth.domain.Usuario;
import com.tecozam.bills.auth.domain.UsuarioCampo;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioCampoRepository;
import com.tecozam.bills.auth.infrastructure.persistence.UsuarioRepository;
import com.tecozam.bills.prestamo.domain.Prestamo;
import com.tecozam.bills.prestamo.infrastructure.persistence.PrestamoRepository;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.trabajador.domain.Trabajador;
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
    private final UsuarioCampoRepository usuarioCampoRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<AlertaDTO> findPendientes() {
        return alertaPrestamoRepository.findByLeidaFalseOrderByFechaAlertaDesc().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AlertaDTO> findMisPendientes(String username) {
        Trabajador trabajador = resolveTrabajador(username);
        if (trabajador == null) {
            log.warn("Usuario {} sin trabajador asociado, devolviendo lista vacía", username);
            return List.of();
        }
        return alertaPrestamoRepository
                .findByLeidaFalseAndPrestamoTrabajadorIdOrderByFechaAlertaDesc(trabajador.getId())
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public long countPendientes() {
        return alertaPrestamoRepository.countByLeidaFalse();
    }

    @Transactional(readOnly = true)
    public long countMisPendientes(String username) {
        Trabajador trabajador = resolveTrabajador(username);
        if (trabajador == null) return 0L;
        return alertaPrestamoRepository.countByLeidaFalseAndPrestamoTrabajadorId(trabajador.getId());
    }

    private Trabajador resolveTrabajador(String username) {
        UsuarioCampo campo = usuarioCampoRepository.findByUsername(username).orElse(null);
        if (campo != null) {
            return campo.getTrabajador();
        }
        Usuario legacy = usuarioRepository.findByUsername(username).orElse(null);
        return legacy != null ? legacy.getTrabajador() : null;
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
