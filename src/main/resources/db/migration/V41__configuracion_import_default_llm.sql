-- =============================================================================
-- V41__configuracion_import_default_llm.sql
-- Tecozam Bills Manager - El cliente prefiere el import por IA; se activa
-- por defecto. No se borra el parser determinista (Repsol/Moeve) — sigue
-- disponible desactivando el interruptor en /configuracion/import.
--
-- V26 crea la fila unica con modo_llm_activo=0; esta migracion actualiza la
-- fila ya existente (el DEFAULT de la columna no afecta filas insertadas).
-- UPDATE sin condicion es idempotente por si se re-ejecuta.
-- =============================================================================

UPDATE [dbo].[configuracion_import] SET [modo_llm_activo] = 1;
