package com.tecozam.bills.audit.infrastructure.web;

import com.tecozam.bills.audit.domain.AuditLog;
import com.tecozam.bills.audit.dto.AuditLogDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Auditoría", description = "Consulta del log de auditoría (solo ADMIN)")
public class AuditController {

    private final EntityManager entityManager;

    @GetMapping
    public List<AuditLogDTO> findAll(
            @RequestParam(required = false) String tabla,
            @RequestParam(required = false) String accion,
            @RequestParam(defaultValue = "100") int limit
    ) {
        StringBuilder jpql = new StringBuilder("SELECT a FROM AuditLog a WHERE 1=1");
        if (tabla != null && !tabla.isBlank()) jpql.append(" AND a.tabla = :tabla");
        if (accion != null && !accion.isBlank()) jpql.append(" AND a.accion = :accion");
        jpql.append(" ORDER BY a.fecha DESC");

        TypedQuery<AuditLog> query = entityManager.createQuery(jpql.toString(), AuditLog.class);
        if (tabla != null && !tabla.isBlank()) query.setParameter("tabla", tabla);
        if (accion != null && !accion.isBlank()) query.setParameter("accion", accion);
        query.setMaxResults(limit);

        return query.getResultList().stream()
                .map(a -> new AuditLogDTO(a.getId(), a.getTabla(), a.getRegistroId(),
                        a.getAccion(), a.getUsuarioId(),
                        a.getDatosAnteriores(), a.getDatosNuevos(), a.getFecha()))
                .toList();
    }
}
