package com.tecozam.bills.admin.application;

import java.util.List;

/**
 * Respuesta del endpoint admin {@code POST /api/admin/tarjetas/reclasificar-viats}.
 *
 * @param dryRun     true si se pasó {@code dryRun=true}
 * @param candidatos número de VIATs detectados como mal clasificados
 * @param movidos    número de VIATs movidos al maestro Tarjeta (0 si dryRun)
 * @param numeros    primeros 50 números afectados, para preview en UI
 */
public record ReclasificarViatsResponse(
        boolean dryRun,
        int candidatos,
        int movidos,
        List<String> numeros
) {
}
