-- =============================================================================
-- V33__auditoria_reclasificacion_tarjetas.sql
-- Tabla de auditoría para la reclasificación automática TARJETA->VIAT de
-- Cepsa/Moeve por historial real de operaciones (odd/tasks/
-- bugs-fecha-nan-y-reclasificacion-cepsa.md). Dirección inversa de
-- auditoria_reclasificacion_viats (V14): aquí se parte de una Tarjeta del
-- maestro (que se borra tras el movimiento) y se llega a un Viat.
-- tarjeta_id_origen NO lleva FK a [tarjetas]: la fila referenciada se borra
-- en la misma transacción que este INSERT (mismo criterio ya usado en
-- auditoria_reclasificacion_viats para viat_id_origen).
-- Target: Microsoft SQL Server (T-SQL)
-- =============================================================================

CREATE TABLE [dbo].[auditoria_reclasificacion_tarjetas] (
    [id]                 BIGINT        IDENTITY(1,1) NOT NULL,
    [tarjeta_id_origen]  BIGINT        NOT NULL,
    [numero]             NVARCHAR(50)  NOT NULL,
    [viat_id_dest]        BIGINT        NOT NULL,
    [total_operaciones]  INT           NOT NULL,
    [operaciones_peaje]  INT           NOT NULL,
    [ejecutado_por]      NVARCHAR(80)  NULL,
    [ejecutado_en]       DATETIME2     NOT NULL DEFAULT GETDATE(),

    CONSTRAINT [PK_auditoria_reclasificacion_tarjetas] PRIMARY KEY CLUSTERED ([id]),
    CONSTRAINT [FK_aud_reclasif_tarjetas_viat] FOREIGN KEY ([viat_id_dest])
        REFERENCES [dbo].[viats] ([id]) ON DELETE NO ACTION
);
GO

CREATE NONCLUSTERED INDEX [IX_aud_reclasif_tarjetas_numero]
    ON [dbo].[auditoria_reclasificacion_tarjetas]([numero]);
GO
