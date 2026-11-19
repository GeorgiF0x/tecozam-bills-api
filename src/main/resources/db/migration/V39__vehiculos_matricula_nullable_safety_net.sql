-- =============================================================================
-- V39__vehiculos_matricula_nullable_safety_net.sql
-- Tecozam Bills Manager - Red de seguridad idempotente para V38
--
-- Tras desplegar V38 en el backend de pruebas (bills-api.z-innova.com) se
-- comprobo en vivo que la primera maquinaria industrial sin matricula se
-- crea bien, pero la SEGUNDA sigue fallando con 500 (violacion de clave
-- unica). Eso solo se explica si la UNIQUE CONSTRAINT clasica original
-- (UQ_vehiculos_matricula, creada en V3) todavia existe: V37 pretendia
-- sustituirla por un filtered index, pero si V37 fallo en silencio sobre
-- esta BD concreta (mismo patron ya visto en V15, ver flyway_bug_pattern),
-- la constraint clasica seguiria activa e independiente del filtered index
-- que gestiona V38 — y SQL Server solo permite UN valor NULL por columna
-- con una UNIQUE constraint clasica.
--
-- Esta migracion no asume que V37/V38 tuvieron exito: vuelve a comprobar y
-- forzar el estado final completo (constraint fuera, columna nullable,
-- filtered index correcto), de forma idempotente. Detecta la constraint
-- por su columna (igual que V11), no solo por su nombre esperado, para
-- cubrir el caso de que tenga un nombre distinto al esperado.
--
-- Sintaxis plana idempotente (sin BEGIN/END/GO), mismo patron seguro que
-- V23/V34/V35/V37/V38.
-- =============================================================================

DECLARE @uq_name NVARCHAR(255);

SELECT @uq_name = kc.name
FROM sys.key_constraints kc
JOIN sys.index_columns ic
    ON ic.object_id = kc.parent_object_id
   AND ic.index_id  = kc.unique_index_id
JOIN sys.columns c
    ON c.object_id  = ic.object_id
   AND c.column_id  = ic.column_id
WHERE kc.parent_object_id = OBJECT_ID('dbo.vehiculos')
  AND kc.type = 'UQ'
  AND c.name = 'matricula';

IF @uq_name IS NOT NULL
    EXEC ('ALTER TABLE [dbo].[vehiculos] DROP CONSTRAINT [' + @uq_name + ']');

IF EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_vehiculos_matricula_activo'
      AND object_id = OBJECT_ID('dbo.vehiculos')
)
    DROP INDEX [UX_vehiculos_matricula_activo] ON [dbo].[vehiculos];

IF (
    SELECT is_nullable FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.vehiculos') AND name = 'matricula'
) = 0
    ALTER TABLE [dbo].[vehiculos] ALTER COLUMN [matricula] VARCHAR(20) NULL;

SET QUOTED_IDENTIFIER ON;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_vehiculos_matricula_activo'
      AND object_id = OBJECT_ID('dbo.vehiculos')
)
    CREATE UNIQUE NONCLUSTERED INDEX [UX_vehiculos_matricula_activo]
        ON [dbo].[vehiculos]([matricula])
        WHERE [eliminado_en] IS NULL AND [matricula] IS NOT NULL;
