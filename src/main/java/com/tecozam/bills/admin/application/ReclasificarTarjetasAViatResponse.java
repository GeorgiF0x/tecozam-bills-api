package com.tecozam.bills.admin.application;

import java.util.List;

/**
 * Resultado de {@link ReclasificarTarjetasAViatService#reclasificarTarjetasTocadas}.
 *
 * @param dryRun              true si se evaluó sin mover nada
 * @param candidatos          nº de tarjetas que cumplen el umbral (3+ operaciones, 100% peaje)
 * @param movidas             nº de tarjetas efectivamente movidas al maestro Viat (0 si dryRun)
 * @param protegidas          nº de candidatas NO movidas por tener tickets o asignaciones activas
 *                            vinculados por FK directa (ver investigación de riesgo en el informe de la tarea)
 * @param numerosMovidos      números de tarjeta movidos (o que se moverían, en dryRun)
 * @param numerosProtegidos   números de tarjeta candidatas pero protegidas, con el motivo
 */
public record ReclasificarTarjetasAViatResponse(
        boolean dryRun,
        int candidatos,
        int movidas,
        int protegidas,
        List<String> numerosMovidos,
        List<String> numerosProtegidos
) {
}
