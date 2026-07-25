-- =============================================================================
-- V22__tickets_eliminado_en.sql
-- Tecozam Bills Manager - Borrado logico de tickets
--
-- Añade 'eliminado_en' a 'tickets' (Ticket pasa a extender BaseEntity), para
-- poder borrar tickets desde el admin sin perder el rastro de auditoria
-- (a diferencia de un DELETE fisico). Idempotente, mismo patron que V8 para
-- centros_coste/vehiculos/viats/tarjetas.
-- =============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'eliminado_en'
               AND Object_ID = Object_ID(N'dbo.tickets'))
BEGIN
    ALTER TABLE [dbo].[tickets] ADD [eliminado_en] DATETIME2 NULL;
END
GO
