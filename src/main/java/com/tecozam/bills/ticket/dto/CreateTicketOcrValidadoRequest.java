package com.tecozam.bills.ticket.dto;

import com.tecozam.bills.vehiculo.domain.CategoriaRecurso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateTicketOcrValidadoRequest(
        @NotNull Long tarjetaId,
        @NotBlank @Pattern(regexp = "^\\d{4}$", message = "El PIN debe tener exactamente 4 dígitos numéricos") String pin,
        @NotNull CategoriaRecurso categoriaRecurso,
        @NotNull Long vehiculoId,
        @NotNull Long centroCosteId,
        Integer kilometros
) {}
