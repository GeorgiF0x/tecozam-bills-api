-- =============================================================================
-- V31 — amplia 'tickets.observaciones' de VARCHAR(255) a NVARCHAR(MAX).
--
-- Bug real encontrado en produccion/demo: un ticket compuesto de N fotos
-- (ver odd/tasks/ticket-multi-foto.md) puede generar un aviso de revision
-- por cada linea con un problema de plausibilidad (precio/litro fuera de
-- rango, etc.), concatenados en observaciones. Con 2+ lineas problematicas
-- el texto ya supera los 255 caracteres y el INSERT falla con
-- "String or binary data would be truncated" (HTTP 500 real, el ticket NO
-- se llega a guardar). Mismo tipo ya usado para el campo equivalente de
-- facturas (Factura.avisosImport, NVARCHAR(MAX)).
-- =============================================================================

ALTER TABLE [dbo].[tickets] ALTER COLUMN [observaciones] NVARCHAR(MAX) NULL;
