-- =============================================================================
-- V10__fix_seeds_and_vehiculos.sql
-- Tecozam Bills Manager - Cierre del drift detectado en smoke test pre-entrega
--
-- Resuelve 3 problemas detectados al arrancar contra BD limpia:
--   1) vehiculos.categoria y vehiculos.codigo_obra ausentes (Vehiculo.java)
--   2) Bug en V8: enum EstadoRegistro acepta {PENDIENTE,ACTIVO,RECHAZADO}
--      pero V8 inserta 'APROBADO' y el CHECK constraint lo permitía
--   3) Hash BCrypt del seed admin no correspondía a 'admin123', login fallaba
--
-- Idempotente. Solo aplica los UPDATE si el dato problemático aún existe.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────
-- 1) vehiculos: añadir categoria + codigo_obra
-- ─────────────────────────────────────────────────────────────────

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'categoria'
               AND Object_ID = Object_ID(N'dbo.vehiculos'))
BEGIN
    ALTER TABLE [dbo].[vehiculos]
        ADD [categoria] VARCHAR(30) NOT NULL
        CONSTRAINT [DF_vehiculos_categoria] DEFAULT 'VEHICULO';
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'codigo_obra'
               AND Object_ID = Object_ID(N'dbo.vehiculos'))
BEGIN
    ALTER TABLE [dbo].[vehiculos] ADD [codigo_obra] VARCHAR(50) NULL;
END
GO

-- ─────────────────────────────────────────────────────────────────
-- 2) usuarios_oficina: enum EstadoRegistro - reemplazar APROBADO por ACTIVO
-- ─────────────────────────────────────────────────────────────────

-- 2.1) Drop CHECK constraint anterior (puede llamarse de varias formas)
DECLARE @ck_name NVARCHAR(255);
SELECT @ck_name = name
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID('dbo.usuarios_oficina')
  AND definition LIKE '%estado_registro%';
IF @ck_name IS NOT NULL
    EXEC ('ALTER TABLE [dbo].[usuarios_oficina] DROP CONSTRAINT ' + @ck_name);
GO

-- 2.2) Normalizar datos existentes
UPDATE [dbo].[usuarios_oficina]
   SET [estado_registro] = 'ACTIVO'
 WHERE [estado_registro] = 'APROBADO';
GO

-- 2.3) Volver a crear el constraint con los valores correctos del enum
IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id = OBJECT_ID('dbo.usuarios_oficina')
               AND definition LIKE '%estado_registro%')
BEGIN
    ALTER TABLE [dbo].[usuarios_oficina]
        ADD CONSTRAINT [CK_usuarios_oficina_estado]
        CHECK ([estado_registro] IN ('PENDIENTE', 'ACTIVO', 'RECHAZADO'));
END
GO

-- Mismo arreglo en usuarios_campo (mismo enum)
DECLARE @ck_name2 NVARCHAR(255);
SELECT @ck_name2 = name
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID('dbo.usuarios_campo')
  AND definition LIKE '%estado_registro%';
IF @ck_name2 IS NOT NULL
    EXEC ('ALTER TABLE [dbo].[usuarios_campo] DROP CONSTRAINT ' + @ck_name2);
GO

UPDATE [dbo].[usuarios_campo]
   SET [estado_registro] = 'ACTIVO'
 WHERE [estado_registro] = 'APROBADO';
GO

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id = OBJECT_ID('dbo.usuarios_campo')
               AND definition LIKE '%estado_registro%')
BEGIN
    ALTER TABLE [dbo].[usuarios_campo]
        ADD CONSTRAINT [CK_usuarios_campo_estado]
        CHECK ([estado_registro] IN ('PENDIENTE', 'ACTIVO', 'RECHAZADO'));
END
GO

-- ─────────────────────────────────────────────────────────────────
-- 3) Fix BCrypt seed admin (el hash anterior NO correspondía a 'admin123')
-- Solo se actualiza si el hash sigue siendo el defectuoso original,
-- para no pisar passwords que el cliente haya cambiado.
-- ─────────────────────────────────────────────────────────────────

UPDATE [dbo].[usuarios_oficina]
   SET [password] = '$2b$12$28eyhoVwkSkltS4tCDQKrerwXYkJqo/w57CqY8zQTiUg78XPG6I0e'
 WHERE [username] = 'admin'
   AND [password] = '$2a$12$LJ3m4ys3uz0GHpcgMg/4Au1TeT0mk.dFRqkFKz8nUqY1S3Z3JXfMa';
GO

UPDATE [dbo].[usuarios]
   SET [password] = '$2b$12$28eyhoVwkSkltS4tCDQKrerwXYkJqo/w57CqY8zQTiUg78XPG6I0e'
 WHERE [username] = 'admin'
   AND [password] = '$2a$12$LJ3m4ys3uz0GHpcgMg/4Au1TeT0mk.dFRqkFKz8nUqY1S3Z3JXfMa';
GO
