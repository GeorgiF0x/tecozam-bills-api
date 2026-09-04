-- =============================================================================
-- V24 — columna para avisos de plausibilidad detectados al importar una
-- factura (ver FacturaImportValidator). Sintaxis plana (sin BEGIN/END/GO),
-- siguiendo el patron ya usado en V20/V23 tras el problema real de Flyway en
-- el VPS donde ese bloque se registraba como aplicado sin ejecutar el DDL.
-- =============================================================================

IF COL_LENGTH('dbo.facturas', 'avisos_import') IS NULL
    ALTER TABLE [dbo].[facturas] ADD [avisos_import] NVARCHAR(MAX) NULL;
