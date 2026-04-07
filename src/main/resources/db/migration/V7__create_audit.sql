-- =============================================================================
-- V7__create_audit.sql
-- Tecozam Bills Manager - Audit log
-- Target: Microsoft SQL Server (T-SQL)
-- =============================================================================

-- -------------------------------------------------------------------------
-- Table: audit_log
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[audit_log] (
    [id]                BIGINT          IDENTITY(1,1) NOT NULL,
    [tabla]             VARCHAR(100)    NOT NULL,
    [registro_id]       BIGINT          NOT NULL,
    [accion]            VARCHAR(10)     NOT NULL,
    [usuario_id]        BIGINT          NULL,
    [datos_anteriores]  NVARCHAR(MAX)   NULL,          -- JSON payload
    [datos_nuevos]      NVARCHAR(MAX)   NULL,          -- JSON payload
    [fecha]             DATETIME2       NOT NULL DEFAULT GETDATE(),

    CONSTRAINT [PK_audit_log] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [CK_audit_log_accion] CHECK ([accion] IN ('INSERT', 'UPDATE', 'DELETE')),
    CONSTRAINT [FK_audit_log_usuario] FOREIGN KEY ([usuario_id])
        REFERENCES [dbo].[usuarios] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Indexes: audit_log
-- -------------------------------------------------------------------------
CREATE NONCLUSTERED INDEX [IX_audit_log_tabla_registro]
    ON [dbo].[audit_log] ([tabla], [registro_id]);
GO

CREATE NONCLUSTERED INDEX [IX_audit_log_usuario_fecha]
    ON [dbo].[audit_log] ([usuario_id], [fecha]);
GO
