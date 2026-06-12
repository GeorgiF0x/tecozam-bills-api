-- =============================================================================
-- V15 — BILLS-10 — Modelo Trabajador unificado: añadir columna 'origen' para
--       trazar de qué vía proviene cada registro y permitir mostrarlo en los
--       listados. Backfill basado en heurística:
--         - Si tiene UsuarioOficina asociado → OFICINA
--         - Si tiene UsuarioCampo asociado    → CAMPO
--         - En otro caso (importaciones)      → IMPORTACION
-- =============================================================================

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE Name = N'origen' AND Object_ID = Object_ID(N'dbo.trabajadores')
)
BEGIN
    ALTER TABLE [dbo].[trabajadores]
        ADD [origen] NVARCHAR(20) NOT NULL
            CONSTRAINT DF_trabajadores_origen DEFAULT 'IMPORTACION';
END;
GO

-- Backfill: marcar como OFICINA los trabajadores referenciados por usuarios_oficina
IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'usuarios_oficina')
BEGIN
    UPDATE t
        SET t.origen = 'OFICINA'
    FROM [dbo].[trabajadores] t
    INNER JOIN [dbo].[usuarios_oficina] uo ON uo.trabajador_id = t.id
    WHERE t.origen <> 'OFICINA';
END;
GO

-- Backfill: marcar como CAMPO los trabajadores referenciados por usuarios_campo
IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'usuarios_campo')
BEGIN
    UPDATE t
        SET t.origen = 'CAMPO'
    FROM [dbo].[trabajadores] t
    INNER JOIN [dbo].[usuarios_campo] uc ON uc.trabajador_id = t.id
    WHERE t.origen NOT IN ('OFICINA', 'CAMPO');
END;
GO
