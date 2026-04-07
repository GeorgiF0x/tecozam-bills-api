package com.tecozam.bills.asistente.dto;

import jakarta.validation.constraints.NotBlank;

public record AsistenteRequest(
    @NotBlank(message = "La consulta no puede estar vacía")
    String query
) {}
