package com.tecozam.bills.trabajador.application;

import com.tecozam.bills.shared.infrastructure.exception.BusinessException;
import com.tecozam.bills.shared.infrastructure.exception.ResourceNotFoundException;
import com.tecozam.bills.trabajador.domain.Trabajador;
import com.tecozam.bills.trabajador.dto.FusionarTrabajadoresResponse;
import com.tecozam.bills.trabajador.infrastructure.persistence.TrabajadorRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fusión de Trabajadores duplicados (BILLS-10).
 *
 * <p>Re-vincula todas las referencias del trabajador "perdedor" al trabajador
 * "ganador" y borra el perdedor. Las tablas afectadas son:
 *
 * <ul>
 *   <li>{@code tarjeta_asignaciones.trabajador_id}</li>
 *   <li>{@code tickets.trabajador_id}</li>
 *   <li>{@code prestamos.trabajador_id}</li>
 *   <li>{@code usuarios_oficina.trabajador_id} — solo si el ganador no tiene uno</li>
 *   <li>{@code usuarios_campo.trabajador_id} — solo si el ganador no tiene uno</li>
 *   <li>{@code usuarios.trabajador_id} (tabla legacy)</li>
 * </ul>
 *
 * <p>Si el ganador y el perdedor tienen AMBOS un usuario de oficina o ambos un
 * usuario de campo, la operación falla — eso indica que son dos personas reales
 * distintas con cuentas distintas y la fusión debe resolverse a mano.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FusionarTrabajadoresService {

    private final TrabajadorRepository trabajadorRepository;
    private final EntityManager em;

    public FusionarTrabajadoresResponse fusionar(Long ganadorId, Long perdedorId) {
        if (ganadorId == null || perdedorId == null) {
            throw new BusinessException("Faltan IDs de trabajador ganador y perdedor");
        }
        if (ganadorId.equals(perdedorId)) {
            throw new BusinessException("No puedes fusionar un trabajador consigo mismo");
        }

        Trabajador ganador = trabajadorRepository.findById(ganadorId)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador (ganador)", ganadorId));
        Trabajador perdedor = trabajadorRepository.findById(perdedorId)
                .orElseThrow(() -> new ResourceNotFoundException("Trabajador (perdedor)", perdedorId));

        validarConflictoUsuario("usuarios_oficina", ganador.getId(), perdedor.getId());
        validarConflictoUsuario("usuarios_campo", ganador.getId(), perdedor.getId());

        int asignaciones = ejecutarMove("tarjeta_asignaciones", ganador.getId(), perdedor.getId());
        int tickets = ejecutarMove("tickets", ganador.getId(), perdedor.getId());
        int prestamos = ejecutarMove("prestamos", ganador.getId(), perdedor.getId());
        int oficinaMovidos = ejecutarMove("usuarios_oficina", ganador.getId(), perdedor.getId());
        int campoMovidos = ejecutarMove("usuarios_campo", ganador.getId(), perdedor.getId());
        int legacyMovidos = ejecutarMove("usuarios", ganador.getId(), perdedor.getId());

        trabajadorRepository.delete(perdedor);

        log.info("Trabajadores fusionados: perdedor={} → ganador={} (asignaciones={}, tickets={}, prestamos={}, usuarios={})",
                perdedor.getId(), ganador.getId(),
                asignaciones, tickets, prestamos,
                oficinaMovidos + campoMovidos + legacyMovidos);

        return new FusionarTrabajadoresResponse(
                ganador.getId(),
                perdedor.getId(),
                asignaciones,
                tickets,
                prestamos,
                oficinaMovidos + campoMovidos + legacyMovidos);
    }

    private void validarConflictoUsuario(String tabla, Long ganadorId, Long perdedorId) {
        boolean ganadorTiene = countByTrabajadorId(tabla, ganadorId) > 0;
        boolean perdedorTiene = countByTrabajadorId(tabla, perdedorId) > 0;
        if (ganadorTiene && perdedorTiene) {
            throw new BusinessException(
                    "Ambos trabajadores tienen una cuenta en " + tabla
                            + ". La fusión debe resolverse a mano para no perder ninguna cuenta.");
        }
    }

    private long countByTrabajadorId(String tabla, Long trabajadorId) {
        Number n = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM " + tabla + " WHERE trabajador_id = ?1")
                .setParameter(1, trabajadorId)
                .getSingleResult();
        return n.longValue();
    }

    private int ejecutarMove(String tabla, Long ganadorId, Long perdedorId) {
        return em.createNativeQuery(
                        "UPDATE " + tabla + " SET trabajador_id = ?1 WHERE trabajador_id = ?2")
                .setParameter(1, ganadorId)
                .setParameter(2, perdedorId)
                .executeUpdate();
    }
}
