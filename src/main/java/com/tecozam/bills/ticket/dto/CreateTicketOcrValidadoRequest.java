package com.tecozam.bills.ticket.dto;

import com.tecozam.bills.ticket.domain.TipoGasto;
import com.tecozam.bills.vehiculo.domain.CategoriaRecurso;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Request para crear un ticket validado tras OCR.
 *
 * {@code lineas} es OPCIONAL:
 * - Si viene (no vacía), son las líneas que el conductor ya revisó/editó
 *   tras un preview previo — se usan directamente sin re-llamar a la IA.
 * - Si no viene, el backend hace el OCR ahora con las imágenes como fallback
 *   (flujo legacy, compatibilidad con clientes antiguos).
 *
 * FLEET-01: el campo pin es opcional; sólo se valida formato (4 dígitos) cuando
 * el cliente lo envía. El PIN existe únicamente a efectos de consulta.
 *
 * tipoGasto es OPCIONAL (no @NotNull) a propósito: el despliegue de prod es
 * manual (backend y PWA no siempre se despliegan a la vez), así que una PWA
 * antigua sin este campo no debe romper la subida — TicketService lo trata
 * como COMBUSTIBLE si no viene.
 */
public record CreateTicketOcrValidadoRequest(
        @NotNull Long tarjetaId,
        @Pattern(regexp = "^\\d{4}$", message = "El PIN debe tener exactamente 4 dígitos numéricos") String pin,
        @NotNull CategoriaRecurso categoriaRecurso,
        @NotNull Long vehiculoId,
        @NotNull Long centroCosteId,
        Integer kilometros,
        TipoGasto tipoGasto,
        List<LineaOcrDTO> lineas
) {}
