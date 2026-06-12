package com.tecozam.bills.admin.infrastructure.import_;

/**
 * Tipo de recurso al que pertenece una fila del listado de tarjetas importado.
 *
 * <p>Cada implementación de {@code ListadoTarjetasImporter} (Repsol, Cepsa, ...) se
 * apoya en {@link ConceptoClassifier} para decidir si la fila es {@link #TARJETA}
 * (combustible, ad-blue, staff) o {@link #VIAT} (peajes, autopistas, túneles,
 * portagem). El antiguo enum del {@code RepsolImportService} también
 * contemplaba {@code DESCONOCIDO}; aquí preferimos un default seguro a
 * {@code TARJETA} y reportamos las filas no reconocidas en el contador
 * {@code filasIgnoradas} del DTO.
 */
public enum TipoRecurso {
    TARJETA,
    VIAT
}
