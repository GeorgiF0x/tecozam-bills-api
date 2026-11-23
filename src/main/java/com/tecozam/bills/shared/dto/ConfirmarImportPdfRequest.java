package com.tecozam.bills.shared.dto;

import java.util.List;
import java.util.Map;

/**
 * Cuerpo del endpoint de confirmación de import PDF+IA: las filas que el
 * usuario revisó/editó en el frontend y quiere persistir. El backend
 * SIEMPRE revalida cada fila con las mismas reglas del import Excel antes de
 * guardar nada — el frontend no decide qué es válido.
 */
public record ConfirmarImportPdfRequest(List<Map<String, String>> filas) {
}
