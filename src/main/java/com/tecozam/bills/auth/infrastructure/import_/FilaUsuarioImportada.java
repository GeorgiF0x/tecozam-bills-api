package com.tecozam.bills.auth.infrastructure.import_;

/**
 * Resultado puro (sin BD) de parsear una fila del Excel de import masivo de
 * usuarios — ver odd/tasks/maestros-import-masivo.md T5. Una fila puede ser
 * de tipo OFICINA o CAMPO; los campos que no aplican a ese tipo quedan a
 * null y se ignoran.
 */
public record FilaUsuarioImportada(
        String tipo,
        String username,
        String password,
        String nombre,
        String apellidos,
        String email,
        String dni,
        String telefono,
        String rol,
        String errorValidacion
) {
    public boolean esValida() {
        return errorValidacion == null;
    }
}
