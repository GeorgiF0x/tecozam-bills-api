-- =============================================================================
-- V12__usuarios_provisional_data.sql
-- Tecozam Bills Manager - Datos provisionales en usuarios_oficina/campo
--
-- Cambio funcional: el maestro Trabajador se crea cuando un administrador
-- APRUEBA la cuenta, no durante el signup. Mientras la cuenta está PENDIENTE,
-- los datos del solicitante se guardan en columnas provisionales del propio
-- usuario_*. Al aprobar, se crea el Trabajador con esos datos y se vincula.
--
-- Cambios:
--   - usuarios_campo.dni: provisional, sin cifrar (cuando se cree el
--     Trabajador en la activación, el converter AES lo cifrará).
--   - usuarios_oficina.nombre_completo: nombre + apellidos juntos (el form
--     del admin web los pide unificados).
--   - usuarios_oficina.trabajador_id + FK: vínculo al maestro tras activar.
--
-- Idempotente.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────
-- 1) usuarios_campo: dni provisional
-- ─────────────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'dni'
               AND Object_ID = Object_ID(N'dbo.usuarios_campo'))
BEGIN
    ALTER TABLE [dbo].[usuarios_campo] ADD [dni] VARCHAR(20) NULL;
END
GO

-- ─────────────────────────────────────────────────────────────────
-- 2) usuarios_oficina: nombre_completo provisional + FK trabajador
-- ─────────────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'nombre_completo'
               AND Object_ID = Object_ID(N'dbo.usuarios_oficina'))
BEGIN
    ALTER TABLE [dbo].[usuarios_oficina] ADD [nombre_completo] VARCHAR(200) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'trabajador_id'
               AND Object_ID = Object_ID(N'dbo.usuarios_oficina'))
BEGIN
    ALTER TABLE [dbo].[usuarios_oficina] ADD [trabajador_id] BIGINT NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE name = 'FK_usuarios_oficina_trabajador')
BEGIN
    ALTER TABLE [dbo].[usuarios_oficina]
        ADD CONSTRAINT [FK_usuarios_oficina_trabajador]
            FOREIGN KEY ([trabajador_id])
            REFERENCES [dbo].[trabajadores] ([id]) ON DELETE NO ACTION;
END
GO
