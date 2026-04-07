-- =============================================================================
-- V3__create_maestros.sql
-- Tecozam Bills Manager - Master data tables
-- Target: Microsoft SQL Server (T-SQL)
-- =============================================================================

-- -------------------------------------------------------------------------
-- Table: proveedores
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[proveedores] (
    [id]      BIGINT        IDENTITY(1,1) NOT NULL,
    [codigo]  VARCHAR(50)   NOT NULL,
    [nombre]  VARCHAR(150)  NOT NULL,
    [nif]     VARCHAR(20)   NULL,
    [activo]  BIT           NOT NULL DEFAULT 1,

    CONSTRAINT [PK_proveedores] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [UQ_proveedores_codigo] UNIQUE ([codigo])
);
GO

-- -------------------------------------------------------------------------
-- Table: vehiculos
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[vehiculos] (
    [id]             BIGINT        IDENTITY(1,1) NOT NULL,
    [matricula]      VARCHAR(20)   NOT NULL,
    [tipo]           VARCHAR(30)   NOT NULL,
    [descripcion]    VARCHAR(300)  NULL,
    [estado]         VARCHAR(20)   NOT NULL DEFAULT 'DISPONIBLE',
    [activo]         BIT           NOT NULL DEFAULT 1,
    [creado_en]      DATETIME2     NOT NULL DEFAULT GETDATE(),
    [modificado_en]  DATETIME2     NULL,

    CONSTRAINT [PK_vehiculos] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [UQ_vehiculos_matricula] UNIQUE ([matricula]),
    CONSTRAINT [CK_vehiculos_tipo] CHECK ([tipo] IN ('TURISMO', 'FURGONETA', 'CAMION', 'MAQUINARIA')),
    CONSTRAINT [CK_vehiculos_estado] CHECK ([estado] IN ('DISPONIBLE', 'PRESTADO', 'BLOQUEADO', 'BAJA'))
);
GO

-- -------------------------------------------------------------------------
-- Table: tarjetas
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[tarjetas] (
    [id]              BIGINT        IDENTITY(1,1) NOT NULL,
    [numero_tarjeta]  VARCHAR(50)   NOT NULL,
    [alias]           VARCHAR(100)  NULL,
    [proveedor_id]    BIGINT        NOT NULL,
    [estado]          VARCHAR(20)   NOT NULL DEFAULT 'DISPONIBLE',
    [activa]          BIT           NOT NULL DEFAULT 1,
    [creado_en]       DATETIME2     NOT NULL DEFAULT GETDATE(),
    [modificado_en]   DATETIME2     NULL,

    CONSTRAINT [PK_tarjetas] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [UQ_tarjetas_numero_tarjeta] UNIQUE ([numero_tarjeta]),
    CONSTRAINT [FK_tarjetas_proveedor] FOREIGN KEY ([proveedor_id])
        REFERENCES [dbo].[proveedores] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Table: tarjeta_asignaciones
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[tarjeta_asignaciones] (
    [id]              BIGINT  IDENTITY(1,1) NOT NULL,
    [tarjeta_id]      BIGINT  NOT NULL,
    [trabajador_id]   BIGINT  NOT NULL,
    [vehiculo_id]     BIGINT  NULL,
    [fecha_desde]     DATE    NOT NULL,
    [fecha_hasta]     DATE    NULL,

    CONSTRAINT [PK_tarjeta_asignaciones] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [FK_tarjeta_asignaciones_tarjeta] FOREIGN KEY ([tarjeta_id])
        REFERENCES [dbo].[tarjetas] ([id]) ON DELETE NO ACTION,
    CONSTRAINT [FK_tarjeta_asignaciones_trabajador] FOREIGN KEY ([trabajador_id])
        REFERENCES [dbo].[trabajadores] ([id]) ON DELETE NO ACTION,
    CONSTRAINT [FK_tarjeta_asignaciones_vehiculo] FOREIGN KEY ([vehiculo_id])
        REFERENCES [dbo].[vehiculos] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Table: viats
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[viats] (
    [id]             BIGINT        IDENTITY(1,1) NOT NULL,
    [codigo]         VARCHAR(50)   NOT NULL,
    [numero_serie]   VARCHAR(100)  NULL,
    [descripcion]    VARCHAR(300)  NULL,
    [estado]         VARCHAR(20)   NOT NULL DEFAULT 'DISPONIBLE',
    [activo]         BIT           NOT NULL DEFAULT 1,
    [creado_en]      DATETIME2     NOT NULL DEFAULT GETDATE(),
    [modificado_en]  DATETIME2     NULL,

    CONSTRAINT [PK_viats] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [UQ_viats_codigo] UNIQUE ([codigo])
);
GO

-- -------------------------------------------------------------------------
-- Table: centros_coste
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[centros_coste] (
    [id]             BIGINT        IDENTITY(1,1) NOT NULL,
    [codigo]         VARCHAR(50)   NOT NULL,
    [nombre]         VARCHAR(120)  NOT NULL,
    [descripcion]    VARCHAR(300)  NULL,
    [activo]         BIT           NOT NULL DEFAULT 1,
    [creado_en]      DATETIME2     NOT NULL DEFAULT GETDATE(),
    [modificado_en]  DATETIME2     NULL,

    CONSTRAINT [PK_centros_coste] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [UQ_centros_coste_codigo] UNIQUE ([codigo])
);
GO

-- -------------------------------------------------------------------------
-- Seed: default proveedores
-- -------------------------------------------------------------------------
INSERT INTO [dbo].[proveedores] ([codigo], [nombre], [activo])
VALUES
    ('REPSOL',      'Repsol',      1),
    ('MOEVE_CEPSA', 'Moeve Cepsa', 1);
GO
