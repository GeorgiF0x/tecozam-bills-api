-- =============================================================================
-- V25__proveedores_eliminado_en.sql
-- Tecozam Bills Manager - Borrado logico de proveedores
--
-- Anade creado_en/modificado_en/eliminado_en a 'proveedores' (Proveedor pasa a
-- extender BaseEntity, igual que vehiculos/viats/centros_coste/trabajadores/
-- tickets/tarjetas), para poder borrar proveedores desde el admin sin perder
-- el rastro de auditoria.
--
-- Sintaxis plana idempotente (sin BEGIN/END/GO): mismo patron seguro que V23,
-- que evita el fallo silencioso ya visto en produccion con IF NOT EXISTS (...)
-- BEGIN ... END GO (V15/V22).
-- =============================================================================

IF COL_LENGTH('dbo.proveedores', 'creado_en') IS NULL
    ALTER TABLE [dbo].[proveedores]
        ADD [creado_en] DATETIME2 NOT NULL
        CONSTRAINT DF_proveedores_creado_en DEFAULT GETDATE();

IF COL_LENGTH('dbo.proveedores', 'modificado_en') IS NULL
    ALTER TABLE [dbo].[proveedores] ADD [modificado_en] DATETIME2 NULL;

IF COL_LENGTH('dbo.proveedores', 'eliminado_en') IS NULL
    ALTER TABLE [dbo].[proveedores] ADD [eliminado_en] DATETIME2 NULL;
