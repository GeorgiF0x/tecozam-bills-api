-- =============================================================================
-- V6__create_prestamos.sql
-- Tecozam Bills Manager - Loans & loan alerts
-- Target: Microsoft SQL Server (T-SQL)
-- =============================================================================

-- -------------------------------------------------------------------------
-- Table: prestamos
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[prestamos] (
    [id]                     BIGINT        IDENTITY(1,1) NOT NULL,
    [tipo_recurso]           VARCHAR(20)   NOT NULL,
    [tarjeta_id]             BIGINT        NULL,
    [viat_id]                BIGINT        NULL,
    [vehiculo_id]            BIGINT        NULL,
    [trabajador_id]          BIGINT        NOT NULL,
    [centro_coste_id]        BIGINT        NOT NULL,
    [tipo_prestamo]          VARCHAR(30)   NOT NULL,
    [estado]                 VARCHAR(30)   NOT NULL DEFAULT 'ACTIVO',
    [fecha_inicio]           DATE          NOT NULL,
    [fecha_fin_prevista]     DATE          NULL,
    [fecha_devolucion_real]  DATE          NULL,
    [observaciones]          VARCHAR(500)  NULL,
    [alerta_3d_enviada]      BIT           NOT NULL DEFAULT 0,
    [alerta_1d_enviada]      BIT           NOT NULL DEFAULT 0,
    [alerta_hoy_enviada]     BIT           NOT NULL DEFAULT 0,
    [email_ultimo_dia]       BIT           NOT NULL DEFAULT 0,
    [creado_en]              DATETIME2     NOT NULL DEFAULT GETDATE(),
    [modificado_en]          DATETIME2     NULL,

    CONSTRAINT [PK_prestamos] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [CK_prestamos_tipo_recurso] CHECK ([tipo_recurso] IN ('TARJETA', 'VIAT', 'VEHICULO')),
    CONSTRAINT [CK_prestamos_tipo_prestamo] CHECK ([tipo_prestamo] IN ('CON_FECHA_FIN', 'HASTA_REPOSICION')),
    CONSTRAINT [CK_prestamos_estado] CHECK ([estado] IN (
        'ACTIVO', 'PENDIENTE_DEVOLUCION', 'VENCIDO', 'DEVUELTO', 'CANCELADO'
    )),
    CONSTRAINT [FK_prestamos_tarjeta] FOREIGN KEY ([tarjeta_id])
        REFERENCES [dbo].[tarjetas] ([id]) ON DELETE NO ACTION,
    CONSTRAINT [FK_prestamos_viat] FOREIGN KEY ([viat_id])
        REFERENCES [dbo].[viats] ([id]) ON DELETE NO ACTION,
    CONSTRAINT [FK_prestamos_vehiculo] FOREIGN KEY ([vehiculo_id])
        REFERENCES [dbo].[vehiculos] ([id]) ON DELETE NO ACTION,
    CONSTRAINT [FK_prestamos_trabajador] FOREIGN KEY ([trabajador_id])
        REFERENCES [dbo].[trabajadores] ([id]) ON DELETE NO ACTION,
    CONSTRAINT [FK_prestamos_centro_coste] FOREIGN KEY ([centro_coste_id])
        REFERENCES [dbo].[centros_coste] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Table: alertas_prestamo
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[alertas_prestamo] (
    [id]              BIGINT        IDENTITY(1,1) NOT NULL,
    [prestamo_id]     BIGINT        NOT NULL,
    [tipo_alerta]     VARCHAR(30)   NOT NULL,
    [fecha_alerta]    DATE          NOT NULL,
    [mensaje]         VARCHAR(500)  NULL,
    [email_enviado]   BIT           NOT NULL DEFAULT 0,
    [leida]           BIT           NOT NULL DEFAULT 0,
    [creado_en]       DATETIME2     NOT NULL DEFAULT GETDATE(),

    CONSTRAINT [PK_alertas_prestamo] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [CK_alertas_prestamo_tipo] CHECK ([tipo_alerta] IN (
        'TRES_DIAS_ANTES', 'UN_DIA_ANTES', 'MISMO_DIA', 'VENCIDO'
    )),
    CONSTRAINT [FK_alertas_prestamo_prestamo] FOREIGN KEY ([prestamo_id])
        REFERENCES [dbo].[prestamos] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Indexes: prestamos & alertas
-- -------------------------------------------------------------------------
CREATE NONCLUSTERED INDEX [IX_prestamo_estado]
    ON [dbo].[prestamos] ([estado]);
GO

CREATE NONCLUSTERED INDEX [IX_prestamo_fecha_fin]
    ON [dbo].[prestamos] ([fecha_fin_prevista]);
GO

CREATE NONCLUSTERED INDEX [IX_alerta_leida]
    ON [dbo].[alertas_prestamo] ([leida]);
GO
