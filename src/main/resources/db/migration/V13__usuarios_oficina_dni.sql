-- =============================================================================
-- V13__usuarios_oficina_dni.sql
-- Tecozam Bills Manager - Añadir DNI opcional a usuarios_oficina
--
-- Misma idea que en usuarios_campo (V12): guardar el documento de identidad
-- (DNI español, NIE o documento extranjero) como dato provisional durante
-- el signup. Al aprobar el usuario, se vuelca al Trabajador maestro.
--
-- Idempotente.
-- =============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'dni'
               AND Object_ID = Object_ID(N'dbo.usuarios_oficina'))
BEGIN
    ALTER TABLE [dbo].[usuarios_oficina] ADD [dni] VARCHAR(20) NULL;
END
GO
