package com.tecozam.bills.trabajador.domain;

/**
 * Origen del registro de un Trabajador (BILLS-10).
 *
 * <p>Este enum se persiste como string en la columna {@code trabajadores.origen}
 * para preservar legibilidad en BD. No se debe reordenar ni renombrar sin una
 * migración acompañante.
 */
public enum OrigenTrabajador {
    /** Persona dada de alta desde el alta admin (usuario de oficina). */
    OFICINA,
    /** Persona dada de alta desde el signup o admin como operario de campo. */
    CAMPO,
    /** Persona creada al importar listado de tarjetas o factura. */
    IMPORTACION
}
