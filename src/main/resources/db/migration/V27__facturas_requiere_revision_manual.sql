-- =============================================================================
-- V27 — añade 'requiere_revision_manual' a 'facturas'. Marca facturas
-- importadas con el modo LLM (ver odd/tasks/import-llm-switch.md) que tienen
-- avisos de plausibilidad, para excluirlas del cotejo automatico hasta que un
-- humano las revise. Sintaxis plana idempotente (mismo patron seguro que V23
-- y V25, evita el fallo silencioso ya visto en produccion con
-- IF NOT EXISTS (...) BEGIN ALTER TABLE ... END GO).
-- =============================================================================

IF COL_LENGTH('dbo.facturas', 'requiere_revision_manual') IS NULL
    ALTER TABLE [dbo].[facturas]
        ADD [requiere_revision_manual] BIT NOT NULL
        CONSTRAINT DF_facturas_requiere_revision_manual DEFAULT 0;
