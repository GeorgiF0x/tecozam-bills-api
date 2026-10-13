-- =============================================================================
-- V29 — ticket_lineas gana foto_url: ruta de la foto de origen de la linea,
-- persistida de verdad (antes del T3 de odd/tasks/ticket-multi-foto.md las
-- fotos de ticket se descartaban tras el OCR, igual que le pasaba a
-- Ticket.imagen_url, que nunca se llego a asignar).
-- Sintaxis plana idempotente, mismo patron que V25.
-- =============================================================================

IF COL_LENGTH('dbo.ticket_lineas', 'foto_url') IS NULL
    ALTER TABLE [dbo].[ticket_lineas] ADD [foto_url] NVARCHAR(500) NULL;
