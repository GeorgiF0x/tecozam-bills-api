package com.tecozam.bills.shared.dto;

import java.util.List;

/** Respuesta del endpoint de previsualización de import PDF+IA, para cualquier maestro. */
public record ImportPdfPreviewDTO(List<ColumnaDTO> columnas, List<FilaPreviewDTO> filas) {
}
