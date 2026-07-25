-- =============================================================================
-- V23 — red de seguridad para V22 (tickets.eliminado_en).
--
-- V22 usa el patron IF NOT EXISTS (...) BEGIN ALTER TABLE ... END GO, el mismo
-- que ya fallo una vez en el VPS real (ver V20): Flyway registro V15 como
-- aplicada en flyway_schema_history pero la columna 'trabajadores.origen'
-- nunca llego a crearse. No se modifica V22 directamente porque, si ya se
-- aplico en produccion, tocar su contenido rompe el checksum y el proximo
-- deploy fallaria al arrancar.
--
-- Esta migracion usa sintaxis plana (sin BEGIN/END/GO), igual que V20:
-- si la columna ya existe (V22 SI funciono), no hace nada; si no existe
-- (V22 fallo en silencio, igual que V15), la crea de verdad.
-- =============================================================================

IF COL_LENGTH('dbo.tickets', 'eliminado_en') IS NULL
    ALTER TABLE [dbo].[tickets] ADD [eliminado_en] DATETIME2 NULL;
