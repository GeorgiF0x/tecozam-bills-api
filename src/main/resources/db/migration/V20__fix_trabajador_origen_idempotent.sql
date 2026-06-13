-- =============================================================================
-- V20 — HOTFIX producción: forzar columna trabajadores.origen.
--
-- En el VPS la V15 quedó como aplicada en flyway_schema_history pero la columna
-- NUNCA llegó a crearse (probable bug del parser de Flyway con bloques
-- IF NOT EXISTS BEGIN ... END combinados con GO en SQL Server). Resultado:
-- al cargar /api/prestamos Hibernate genera "select ... t1_0.origen from
-- trabajadores" y SQL Server devuelve "Invalid column name 'origen'".
--
-- Esta migración usa sintaxis plana sin BEGIN/END/GO para que Flyway la
-- ejecute como una sola sentencia. Es idempotente: si la columna ya existe
-- (caso de bases de datos limpias donde V15 sí se aplicó), no hace nada.
-- =============================================================================

IF COL_LENGTH('dbo.trabajadores', 'origen') IS NULL
    ALTER TABLE [dbo].[trabajadores]
        ADD [origen] NVARCHAR(20) NOT NULL
            CONSTRAINT DF_trabajadores_origen DEFAULT 'IMPORTACION';

-- Backfill OFICINA — sin BEGIN/END, idempotente. Solo corre si existe la tabla
-- usuarios_oficina (la query NO falla si trabajador.origen ya es 'OFICINA').
UPDATE t
    SET t.origen = 'OFICINA'
FROM [dbo].[trabajadores] t
INNER JOIN [dbo].[usuarios_oficina] uo ON uo.trabajador_id = t.id
WHERE t.origen <> 'OFICINA';

-- Backfill CAMPO — análogo. Si un trabajador es OFICINA y CAMPO a la vez
-- (caso raro), gana OFICINA por orden de ejecución.
UPDATE t
    SET t.origen = 'CAMPO'
FROM [dbo].[trabajadores] t
INNER JOIN [dbo].[usuarios_campo] uc ON uc.trabajador_id = t.id
WHERE t.origen NOT IN ('OFICINA', 'CAMPO');
