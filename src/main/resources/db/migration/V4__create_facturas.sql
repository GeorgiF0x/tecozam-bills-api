-- =============================================================================
-- V4__create_facturas.sql
-- Tecozam Bills Manager - Invoices, summaries & operations
-- Target: Microsoft SQL Server (T-SQL)
-- =============================================================================

-- -------------------------------------------------------------------------
-- Table: facturas
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[facturas] (
    [id]              BIGINT          IDENTITY(1,1) NOT NULL,
    [proveedor_id]    BIGINT          NOT NULL,
    [num_factura]     VARCHAR(100)    NOT NULL,
    [fecha]           DATE            NULL,
    [periodo_desde]   DATE            NULL,
    [periodo_hasta]   DATE            NULL,
    [num_cuenta]      VARCHAR(50)     NULL,
    [nif_cliente]     VARCHAR(20)     NULL,
    [nombre_cliente]  VARCHAR(200)    NULL,
    [base_imponible]  DECIMAL(12,2)   NULL,
    [total_iva]       DECIMAL(12,2)   NULL,
    [total_factura]   DECIMAL(12,2)   NULL,
    [vencimiento]     DATE            NULL,
    [iban]            VARCHAR(500)    NULL,          -- encrypted at application layer
    [ruta_pdf]        VARCHAR(500)    NULL,
    [creado_en]       DATETIME2       NOT NULL DEFAULT GETDATE(),
    [creado_por]      VARCHAR(80)     NULL,
    [modificado_en]   DATETIME2       NULL,
    [modificado_por]  VARCHAR(80)     NULL,

    CONSTRAINT [PK_facturas] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [UQ_facturas_num_proveedor] UNIQUE ([num_factura], [proveedor_id]),
    CONSTRAINT [FK_facturas_proveedor] FOREIGN KEY ([proveedor_id])
        REFERENCES [dbo].[proveedores] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Table: factura_documentos
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[factura_documentos] (
    [id]               BIGINT         IDENTITY(1,1) NOT NULL,
    [factura_id]       BIGINT         NOT NULL,
    [pais]             VARCHAR(50)    NULL,
    [num_documento]    VARCHAR(100)   NULL,
    [fecha_documento]  DATE           NULL,
    [importe]          DECIMAL(12,2)  NULL,

    CONSTRAINT [PK_factura_documentos] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [FK_factura_documentos_factura] FOREIGN KEY ([factura_id])
        REFERENCES [dbo].[facturas] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Table: factura_concepto_resumen
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[factura_concepto_resumen] (
    [id]                 BIGINT         IDENTITY(1,1) NOT NULL,
    [factura_id]         BIGINT         NOT NULL,
    [concepto_original]  VARCHAR(200)   NULL,
    [concepto_unificado] VARCHAR(30)    NULL,
    [cantidad]           DECIMAL(12,2)  NULL,
    [base_imponible]     DECIMAL(12,2)  NULL,
    [tipo_iva]           DECIMAL(5,2)   NULL,
    [cuota_iva]          DECIMAL(12,2)  NULL,
    [importe]            DECIMAL(12,2)  NULL,

    CONSTRAINT [PK_factura_concepto_resumen] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [FK_factura_concepto_resumen_factura] FOREIGN KEY ([factura_id])
        REFERENCES [dbo].[facturas] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Table: tarjeta_resumenes
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[tarjeta_resumenes] (
    [id]           BIGINT        IDENTITY(1,1) NOT NULL,
    [factura_id]   BIGINT        NOT NULL,
    [tarjeta_id]   BIGINT        NULL,
    [num_tarjeta]  VARCHAR(50)   NULL,
    [alias]        VARCHAR(100)  NULL,
    [conductor]    VARCHAR(200)  NULL,

    CONSTRAINT [PK_tarjeta_resumenes] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [FK_tarjeta_resumenes_factura] FOREIGN KEY ([factura_id])
        REFERENCES [dbo].[facturas] ([id]) ON DELETE NO ACTION,
    CONSTRAINT [FK_tarjeta_resumenes_tarjeta] FOREIGN KEY ([tarjeta_id])
        REFERENCES [dbo].[tarjetas] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Table: tarjeta_resumen_conceptos
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[tarjeta_resumen_conceptos] (
    [id]                  BIGINT         IDENTITY(1,1) NOT NULL,
    [tarjeta_resumen_id]  BIGINT         NOT NULL,
    [concepto_original]   VARCHAR(200)   NULL,
    [concepto_unificado]  VARCHAR(30)    NULL,
    [importe]             DECIMAL(10,2)  NULL,
    [cantidad]            DECIMAL(10,2)  NULL,
    [total_bonificacion]  DECIMAL(10,2)  NULL,

    CONSTRAINT [PK_tarjeta_resumen_conceptos] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [FK_tarjeta_resumen_conceptos_resumen] FOREIGN KEY ([tarjeta_resumen_id])
        REFERENCES [dbo].[tarjeta_resumenes] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Table: operaciones
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[operaciones] (
    [id]                  BIGINT         IDENTITY(1,1) NOT NULL,
    [tarjeta_resumen_id]  BIGINT         NULL,
    [factura_id]          BIGINT         NULL,
    [referencia]          VARCHAR(100)   NULL,
    [fecha_hora]          DATETIME2      NULL,
    [concepto_original]   VARCHAR(200)   NULL,
    [concepto_unificado]  VARCHAR(30)    NULL,
    [establecimiento]     VARCHAR(200)   NULL,
    [kms]                 INT            NULL,
    [cantidad]            DECIMAL(10,2)  NULL,
    [precio_neto]         DECIMAL(8,3)   NULL,
    [precio_iva_inc]      DECIMAL(8,3)   NULL,
    [precio_unitario]     DECIMAL(8,3)   NULL,
    [precio_aplicado]     DECIMAL(8,3)   NULL,
    [importe]             DECIMAL(10,2)  NULL,
    [importe_total]       DECIMAL(10,2)  NULL,
    [dto_porcentaje]      DECIMAL(5,2)   NULL,
    [dto_total]           DECIMAL(10,2)  NULL,
    [bonificacion]        DECIMAL(10,2)  NULL,
    [observaciones]       VARCHAR(100)   NULL,

    CONSTRAINT [PK_operaciones] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [FK_operaciones_tarjeta_resumen] FOREIGN KEY ([tarjeta_resumen_id])
        REFERENCES [dbo].[tarjeta_resumenes] ([id]) ON DELETE NO ACTION,
    CONSTRAINT [FK_operaciones_factura] FOREIGN KEY ([factura_id])
        REFERENCES [dbo].[facturas] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Indexes: operaciones
-- -------------------------------------------------------------------------
CREATE NONCLUSTERED INDEX [IX_operacion_fecha]
    ON [dbo].[operaciones] ([fecha_hora]);
GO

CREATE NONCLUSTERED INDEX [IX_operacion_concepto]
    ON [dbo].[operaciones] ([concepto_unificado]);
GO

CREATE NONCLUSTERED INDEX [IX_operacion_factura]
    ON [dbo].[operaciones] ([factura_id]);
GO
