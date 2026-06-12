package com.tecozam.bills.trabajador.dto;

/**
 * Respuesta del endpoint admin de fusión de trabajadores duplicados (BILLS-10).
 *
 * @param ganadorId           ID del trabajador conservado
 * @param perdedorId          ID del trabajador eliminado
 * @param asignacionesMovidas filas de tarjeta_asignaciones re-vinculadas
 * @param ticketsMovidos      filas de tickets re-vinculadas
 * @param prestamosMovidos    filas de prestamos re-vinculadas
 * @param usuariosMovidos     filas de usuarios_oficina/campo re-vinculadas
 */
public record FusionarTrabajadoresResponse(
        Long ganadorId,
        Long perdedorId,
        int asignacionesMovidas,
        int ticketsMovidos,
        int prestamosMovidos,
        int usuariosMovidos
) {
}
