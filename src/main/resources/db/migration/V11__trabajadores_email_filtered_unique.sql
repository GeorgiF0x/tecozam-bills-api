-- =============================================================================
-- V11__trabajadores_email_filtered_unique.sql
-- Tecozam Bills Manager - Permitir múltiples trabajadores sin email
--
-- SQL Server, a diferencia de PostgreSQL/MySQL, NO permite múltiples NULL
-- en una columna con UNIQUE constraint clásica. Esto impedía registrar
-- más de un operario sin email desde la PWA (signup), aunque la columna
-- esté declarada NULLABLE.
--
-- Solución: reemplazar la UNIQUE constraint por un filtered unique index
-- que solo aplica la restricción cuando email IS NOT NULL.
--
-- Idempotente: detecta el constraint por su definición, no por su nombre.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────
-- 1) Drop de la UNIQUE constraint sobre email (si existe)
-- ─────────────────────────────────────────────────────────────────

DECLARE @uq_name NVARCHAR(255);

SELECT @uq_name = kc.name
FROM sys.key_constraints kc
JOIN sys.index_columns ic
    ON ic.object_id = kc.parent_object_id
   AND ic.index_id  = kc.unique_index_id
JOIN sys.columns c
    ON c.object_id  = ic.object_id
   AND c.column_id  = ic.column_id
WHERE kc.parent_object_id = OBJECT_ID('dbo.trabajadores')
  AND kc.type = 'UQ'
  AND c.name = 'email';

IF @uq_name IS NOT NULL
    EXEC ('ALTER TABLE [dbo].[trabajadores] DROP CONSTRAINT ' + @uq_name);
GO

-- ─────────────────────────────────────────────────────────────────
-- 2) Crear filtered unique index sobre email (excluye NULL)
-- ─────────────────────────────────────────────────────────────────

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_trabajadores_email'
      AND object_id = OBJECT_ID('dbo.trabajadores')
)
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX [UX_trabajadores_email]
        ON [dbo].[trabajadores]([email])
        WHERE [email] IS NOT NULL;
END
GO
