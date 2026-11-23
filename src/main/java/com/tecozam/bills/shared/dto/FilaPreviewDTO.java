package com.tecozam.bills.shared.dto;

import java.util.Map;

/**
 * Una fila candidata extraída por IA de un PDF, pendiente de revisión. Los
 * valores de {@code campos} son siempre String (edición libre en la tabla de
 * revisión del frontend); la validación real de negocio ya se aplicó una vez
 * aquí (mismo validador que el import Excel) para poblar {@code valida}/
 * {@code error}, pero se vuelve a aplicar al confirmar — nunca te fíes de
 * este valor si la fila fue editada en el frontend antes de confirmar.
 */
public record FilaPreviewDTO(Map<String, String> campos, boolean valida, String error) {
}
