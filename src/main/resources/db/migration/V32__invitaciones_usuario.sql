-- =============================================================================
-- V32__invitaciones_usuario.sql
-- Tecozam Bills Manager - Invitaciones de administradores por email
-- Target: Microsoft SQL Server (T-SQL)
-- =============================================================================

-- -------------------------------------------------------------------------
-- Table: invitaciones_usuario
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[invitaciones_usuario] (
    [id]              BIGINT         IDENTITY(1,1) NOT NULL,
    [usuario_id]      BIGINT         NOT NULL,
    [token_hash]      VARCHAR(64)    NOT NULL,
    [expires_at]      DATETIME2      NOT NULL,
    [used_at]         DATETIME2      NULL,
    [invitado_por_id] BIGINT         NOT NULL,
    [creado_en]       DATETIME2      NOT NULL DEFAULT GETDATE(),
    [modificado_en]   DATETIME2      NULL,
    [eliminado_en]    DATETIME2      NULL,

    CONSTRAINT [PK_invitaciones_usuario] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [FK_invitaciones_usuario_usuario] FOREIGN KEY ([usuario_id])
        REFERENCES [dbo].[usuarios_oficina] ([id]) ON DELETE NO ACTION,
    CONSTRAINT [FK_invitaciones_usuario_invitado_por] FOREIGN KEY ([invitado_por_id])
        REFERENCES [dbo].[usuarios_oficina] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Indexes: invitaciones_usuario
-- -------------------------------------------------------------------------
CREATE UNIQUE NONCLUSTERED INDEX [IX_invitaciones_usuario_token_hash]
    ON [dbo].[invitaciones_usuario] ([token_hash]);
GO

CREATE NONCLUSTERED INDEX [IX_invitaciones_usuario_usuario_pendiente]
    ON [dbo].[invitaciones_usuario] ([usuario_id], [used_at]);
GO
