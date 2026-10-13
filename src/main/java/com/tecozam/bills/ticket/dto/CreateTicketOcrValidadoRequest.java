package com.tecozam.bills.ticket.dto;

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
 */
public record CreateTicketOcrValidadoRequest(
        @NotNull Long tarjetaId,
        @Pattern(regexp = "^\\d{4}$", message = "El PIN debe tener exactamente 4 dígitos numéricos") String pin,
        @NotNull CategoriaRecurso categoriaRecurso,
        @NotNull Long vehiculoId,
        @NotNull Long centroCosteId,
        Integer kilometros,
        List<LineaOcrDTO> lineas
) {}
