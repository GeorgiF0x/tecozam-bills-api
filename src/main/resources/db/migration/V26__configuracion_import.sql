-- =============================================================================
-- V26 — tabla de configuracion centralizada para el interruptor regex/LLM en
-- los imports (listado de tarjetas y facturas/extracto). Fila unica, gestionada
-- solo por ADMIN. Idempotente: no falla si ya se aplico parcialmente.
-- =============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables
               WHERE name = 'configuracion_import'
               AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE [dbo].[configuracion_import] (
        [id]               BIGINT        IDENTITY(1,1) NOT NULL,
        [modo_llm_activo]  BIT           NOT NULL DEFAULT 0,
        [actualizado_en]   DATETIME2     NULL,
        [actualizado_por]  VARCHAR(80)   NULL,

        CONSTRAINT [PK_configuracion_import] PRIMARY KEY CLUSTERED ([id])
    );
END

IF NOT EXISTS (SELECT 1 FROM [dbo].[configuracion_import])
    INSERT INTO [dbo].[configuracion_import] ([modo_llm_activo]) VALUES (0);
