-- =============================================================================
-- V34__usuarios_eliminado_en.sql
-- Tecozam Bills Manager - Borrado logico de usuarios de oficina y campo
--
-- Anade 'eliminado_en' a 'usuarios_oficina' y 'usuarios_campo' para poder
-- borrar logicamente estos usuarios sin perder el rastro de auditoria.
-- UsuarioOficina y UsuarioCampo NO extienden BaseEntity (gestionan
-- creado_en/modificado_en manualmente con @PrePersist/@PreUpdate), asi que
-- solo se anade la columna eliminado_en.
--
-- Sintaxis plana idempotente (sin BEGIN/END/GO): mismo patron seguro que
-- V23/V25, que evita el fallo silencioso ya visto en produccion con
-- IF NOT EXISTS (...) BEGIN ... END GO (V15/V22).
-- =============================================================================

IF COL_LENGTH('dbo.usuarios_oficina', 'eliminado_en') IS NULL
    ALTER TABLE [dbo].[usuarios_oficina] ADD [eliminado_en] DATETIME2 NULL;

IF COL_LENGTH('dbo.usuarios_campo', 'eliminado_en') IS NULL
    ALTER TABLE [dbo].[usuarios_campo] ADD [eliminado_en] DATETIME2 NULL;
