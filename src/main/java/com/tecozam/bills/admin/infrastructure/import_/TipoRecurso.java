package com.tecozam.bills.admin.infrastructure.import_;

/**
 * Tipo de recurso al que pertenece una fila del listado de tarjetas importado.
 *
 * <p>El admin declara explícitamente el tipo de todo el lote al importar
 * (checkbox "Este listado son dispositivos VIAT") — no se adivina por el
 * texto del concepto de cada fila. Adivinar por palabras clave (ni por regex
 * ni por LLM) demostró ser poco fiable: conceptos ambiguos como
 * "COMISION AUTOPISTAS" en una tarjeta de combustible normal se clasificaban
 * como {@link #VIAT} en falso (ver odd/tasks/import-llm-switch.md).
 */
public enum TipoRecurso {
    TARJETA,
    VIAT
}
