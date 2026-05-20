-- =============================================================================
-- V9__add_refresh_token_to_usuarios.sql
-- Tecozam Bills Manager - Añadir refresh_token a la tabla usuarios legacy
-- Detectado durante smoke test: la entidad Usuario.java define @Column(name="refresh_token")
-- pero V2 no creó la columna. Causa Schema-validation: missing column [refresh_token].
-- =============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'refresh_token'
               AND Object_ID = Object_ID(N'dbo.usuarios'))
BEGIN
    ALTER TABLE [dbo].[usuarios] ADD [refresh_token] VARCHAR(500) NULL;
END
GO
