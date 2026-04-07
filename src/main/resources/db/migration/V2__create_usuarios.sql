-- =============================================================================
-- V2__create_usuarios.sql
-- Tecozam Bills Manager - Workers & users tables
-- Target: Microsoft SQL Server (T-SQL)
-- =============================================================================

-- -------------------------------------------------------------------------
-- Table: trabajadores
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[trabajadores] (
    [id]             BIGINT        IDENTITY(1,1) NOT NULL,
    [nombre]         VARCHAR(100)  NOT NULL,
    [apellidos]      VARCHAR(100)  NOT NULL,
    [email]          VARCHAR(255)  NULL,
    [dni_nie]        VARCHAR(500)  NULL,           -- encrypted at application layer
    [activo]         BIT           NOT NULL DEFAULT 1,
    [creado_en]      DATETIME2     NOT NULL DEFAULT GETDATE(),
    [modificado_en]  DATETIME2     NULL,
    [eliminado_en]   DATETIME2     NULL,           -- soft delete

    CONSTRAINT [PK_trabajadores] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [UQ_trabajadores_email] UNIQUE ([email])
);
GO

-- -------------------------------------------------------------------------
-- Table: usuarios
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[usuarios] (
    [id]             BIGINT        IDENTITY(1,1) NOT NULL,
    [username]       VARCHAR(80)   NOT NULL,
    [password]       VARCHAR(255)  NOT NULL,        -- BCrypt hash
    [rol]            VARCHAR(20)   NOT NULL,
    [activo]         BIT           NOT NULL DEFAULT 1,
    [trabajador_id]  BIGINT        NULL,
    [creado_en]      DATETIME2     NOT NULL DEFAULT GETDATE(),
    [modificado_en]  DATETIME2     NULL,

    CONSTRAINT [PK_usuarios] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [UQ_usuarios_username] UNIQUE ([username]),
    CONSTRAINT [CK_usuarios_rol] CHECK ([rol] IN ('ADMIN', 'GESTOR', 'CONSULTA')),
    CONSTRAINT [FK_usuarios_trabajador] FOREIGN KEY ([trabajador_id])
        REFERENCES [dbo].[trabajadores] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Seed: default admin user
-- Password is BCrypt hash of 'admin123'
-- -------------------------------------------------------------------------
INSERT INTO [dbo].[usuarios] ([username], [password], [rol], [activo])
VALUES (
    'admin',
    '$2a$12$LJ3m4ys3uz0GHpcgMg/4Au1TeT0mk.dFRqkFKz8nUqY1S3Z3JXfMa',
    'ADMIN',
    1
);
GO
