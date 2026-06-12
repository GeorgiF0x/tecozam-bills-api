-- =============================================================================
-- V14 — Tabla de auditoría para el endpoint de reclasificación de VIATs mal
--       categorizados como tarjetas (BILLS-04 retrofit). Solo schema, sin
--       movimiento de datos: el admin dispara el endpoint cuando esté listo.
-- =============================================================================

IF NOT EXISTS (
    SELECT 1 FROM sys.tables WHERE name = 'auditoria_reclasificacion_viats'
)
BEGIN
    CREATE TABLE [dbo].[auditoria_reclasificacion_viats] (
        [id]              BIGINT IDENTITY(1,1) PRIMARY KEY,
        [viat_id_origen]  BIGINT       NOT NULL,
        [numero]          NVARCHAR(50) NOT NULL,
        [tarjeta_id_dest] BIGINT       NOT NULL,
        [ejecutado_por]   NVARCHAR(80) NULL,
        [ejecutado_en]    DATETIME2    NOT NULL DEFAULT SYSUTCDATETIME()
    );

    CREATE INDEX [IX_aud_reclasif_numero]
        ON [dbo].[auditoria_reclasificacion_viats]([numero]);
END;
GO
