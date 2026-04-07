package com.tecozam.bills.ticket.dto;

public record CotejoResultDTO(
        int cotejados,
        int pendientes,
        int sinCoincidencia,
        int incidencias,
        int multiples
) {}
