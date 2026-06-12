package com.tecozam.bills.admin.infrastructure.import_;

/**
 * Resultado de parsear una fila del Excel de listado de tarjetas, antes de
 * tocar BD. La materialización (crear/actualizar trabajadores, centros, tarjetas
 * o VIATs) la hace el orquestador en una capa separada para mantener al parser
 * como función pura testeable sin mocks.
 *
 * @param numero            número de tarjeta tal como aparece en el Excel
 *                          (sin normalizar canónicamente todavía)
 * @param matricula         matrícula del vehículo o cadena vacía si "SIN MATRICULA"
 * @param nombreCompleto    nombre + apellidos en una sola cadena
 * @param centroCoste       centro de coste/empresa
 * @param concepto          texto de la columna semántica (DES_PRODU/CONCEPTOS)
 * @param tipo              {@link TipoRecurso#TARJETA} o {@link TipoRecurso#VIAT}
 * @param conceptoConocido  true si el concepto se reconoce; false → contar en filasIgnoradas
 */
public record FilaImportada(
        String numero,
        String matricula,
        String nombreCompleto,
        String centroCoste,
        String concepto,
        TipoRecurso tipo,
        boolean conceptoConocido
) {
}
