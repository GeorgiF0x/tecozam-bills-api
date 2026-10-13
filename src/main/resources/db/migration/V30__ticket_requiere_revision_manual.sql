-- =============================================================================
-- V30 — tickets gana requiere_revision_manual: igual que Factura, marca un
-- ticket compuesto por varias fotos cuyas lineas no cuadran entre si (OCR
-- probablemente mal leido) para que un humano lo revise, sin bloquear su
-- creacion (ver odd/tasks/ticket-multi-foto.md, T5).
-- Sintaxis plana idempotente, mismo patron que V25/V27.
-- =============================================================================

IF COL_LENGTH('dbo.tickets', 'requiere_revision_manual') IS NULL
    ALTER TABLE [dbo].[tickets]
        ADD [requiere_revision_manual] BIT NOT NULL
        CONSTRAINT DF_tickets_requiere_revision_manual DEFAULT 0;
