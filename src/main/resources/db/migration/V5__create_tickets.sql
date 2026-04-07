-- =============================================================================
-- V5__create_tickets.sql
-- Tecozam Bills Manager - Tickets (manual & OCR)
-- Target: Microsoft SQL Server (T-SQL)
-- =============================================================================

-- -------------------------------------------------------------------------
-- Table: tickets
-- -------------------------------------------------------------------------
CREATE TABLE [dbo].[tickets] (
    [id]                     BIGINT         IDENTITY(1,1) NOT NULL,
    [origen]                 VARCHAR(10)    NOT NULL,
    [proveedor_id]           BIGINT         NULL,
    [trabajador_id]          BIGINT         NULL,
    [tarjeta_id]             BIGINT         NULL,
    [vehiculo_id]            BIGINT         NULL,
    [estacion]               VARCHAR(150)   NULL,
    [direccion]              VARCHAR(250)   NULL,
    [fecha_hora]             DATETIME2      NULL,
    [num_tarjeta_4ultimos]   VARCHAR(4)     NULL,
    [matricula]              VARCHAR(20)    NULL,
    [kms]                    INT            NULL,
    [producto]               VARCHAR(100)   NULL,
    [litros]                 DECIMAL(10,2)  NULL,
    [precio_litro]           DECIMAL(8,3)   NULL,
    [importe_total]          DECIMAL(10,2)  NOT NULL,
    [num_recibo]             VARCHAR(50)    NULL,
    [nif_estacion]           VARCHAR(20)    NULL,
    [imagen_url]             VARCHAR(500)   NULL,
    [concepto]               VARCHAR(150)   NULL,
    [observaciones]          VARCHAR(500)   NULL,
    [estado_cotejo]          VARCHAR(20)    NOT NULL DEFAULT 'PENDIENTE',
    [operacion_cotejada_id]  BIGINT         NULL,
    [creado_en]              DATETIME2      NOT NULL DEFAULT GETDATE(),
    [modificado_en]          DATETIME2      NULL,

    CONSTRAINT [PK_tickets] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [CK_tickets_origen] CHECK ([origen] IN ('MANUAL', 'OCR')),
    CONSTRAINT [CK_tickets_estado_cotejo] CHECK ([estado_cotejo] IN (
        'PENDIENTE', 'COTEJADO', 'MULTIPLE', 'SIN_COINCIDENCIA', 'INCIDENCIA'
    )),
    CONSTRAINT [FK_tickets_proveedor] FOREIGN KEY ([proveedor_id])
        REFERENCES [dbo].[proveedores] ([id]) ON DELETE NO ACTION,
    CONSTRAINT [FK_tickets_trabajador] FOREIGN KEY ([trabajador_id])
        REFERENCES [dbo].[trabajadores] ([id]) ON DELETE NO ACTION,
    CONSTRAINT [FK_tickets_tarjeta] FOREIGN KEY ([tarjeta_id])
        REFERENCES [dbo].[tarjetas] ([id]) ON DELETE NO ACTION,
    CONSTRAINT [FK_tickets_vehiculo] FOREIGN KEY ([vehiculo_id])
        REFERENCES [dbo].[vehiculos] ([id]) ON DELETE NO ACTION,
    CONSTRAINT [FK_tickets_operacion_cotejada] FOREIGN KEY ([operacion_cotejada_id])
        REFERENCES [dbo].[operaciones] ([id]) ON DELETE NO ACTION
);
GO

-- -------------------------------------------------------------------------
-- Indexes: tickets
-- -------------------------------------------------------------------------
CREATE NONCLUSTERED INDEX [IX_ticket_fecha]
    ON [dbo].[tickets] ([fecha_hora]);
GO

CREATE NONCLUSTERED INDEX [IX_ticket_tarjeta4]
    ON [dbo].[tickets] ([num_tarjeta_4ultimos]);
GO

CREATE NONCLUSTERED INDEX [IX_ticket_estado]
    ON [dbo].[tickets] ([estado_cotejo]);
GO
