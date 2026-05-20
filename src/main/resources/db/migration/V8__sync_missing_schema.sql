-- =============================================================================
-- V8__sync_missing_schema.sql
-- Tecozam Bills Manager - Sincroniza schema con entidades JPA
-- Target: Microsoft SQL Server (T-SQL)
--
-- Esta migración cierra drift acumulado entre @Entity y migraciones V1..V7:
--   - Crea 4 tablas que faltaban (usuarios_oficina, usuarios_campo, tarifas, tarifa_precios)
--   - Añade columnas faltantes a centros_coste, vehiculos, viats, tarjetas, prestamos, tickets
--   - Migra tipos en prestamos (DATE -> DATETIME2)
-- Idempotente: cada bloque se protege con IF NOT EXISTS / IF EXISTS para que
-- pueda aplicarse tanto en entornos limpios como en BDs con drift previo.
-- =============================================================================

-- =========================================================================
-- 1) Soft-delete: añadir 'eliminado_en' a tablas que heredan de BaseEntity
-- =========================================================================

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'eliminado_en'
               AND Object_ID = Object_ID(N'dbo.centros_coste'))
BEGIN
    ALTER TABLE [dbo].[centros_coste] ADD [eliminado_en] DATETIME2 NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'eliminado_en'
               AND Object_ID = Object_ID(N'dbo.vehiculos'))
BEGIN
    ALTER TABLE [dbo].[vehiculos] ADD [eliminado_en] DATETIME2 NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'eliminado_en'
               AND Object_ID = Object_ID(N'dbo.viats'))
BEGIN
    ALTER TABLE [dbo].[viats] ADD [eliminado_en] DATETIME2 NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'eliminado_en'
               AND Object_ID = Object_ID(N'dbo.tarjetas'))
BEGIN
    ALTER TABLE [dbo].[tarjetas] ADD [eliminado_en] DATETIME2 NULL;
END
GO

-- =========================================================================
-- 2) Tarjetas: PIN cifrado
-- =========================================================================

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'pin_encrypted'
               AND Object_ID = Object_ID(N'dbo.tarjetas'))
BEGIN
    ALTER TABLE [dbo].[tarjetas] ADD [pin_encrypted] VARCHAR(500) NULL;
END
GO

-- =========================================================================
-- 3) Préstamos: nueva columna + cambio de tipo fechas (DATE -> DATETIME2)
-- =========================================================================

-- 3.1) Nueva columna creado_por_campo
IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'creado_por_campo'
               AND Object_ID = Object_ID(N'dbo.prestamos'))
BEGIN
    ALTER TABLE [dbo].[prestamos] ADD [creado_por_campo] BIT NOT NULL CONSTRAINT [DF_prestamos_creado_por_campo] DEFAULT 0;
END
GO

-- 3.2) ALTER COLUMN fechas: drop index dependiente, alter, recrear index
IF EXISTS (SELECT 1 FROM sys.indexes
           WHERE name = 'IX_prestamo_fecha_fin'
           AND object_id = OBJECT_ID('dbo.prestamos'))
BEGIN
    DROP INDEX [IX_prestamo_fecha_fin] ON [dbo].[prestamos];
END
GO

IF EXISTS (SELECT 1 FROM sys.columns
           WHERE Name = N'fecha_inicio'
           AND Object_ID = Object_ID(N'dbo.prestamos')
           AND system_type_id = TYPE_ID('date'))
BEGIN
    ALTER TABLE [dbo].[prestamos] ALTER COLUMN [fecha_inicio] DATETIME2 NOT NULL;
END
GO

IF EXISTS (SELECT 1 FROM sys.columns
           WHERE Name = N'fecha_fin_prevista'
           AND Object_ID = Object_ID(N'dbo.prestamos')
           AND system_type_id = TYPE_ID('date'))
BEGIN
    ALTER TABLE [dbo].[prestamos] ALTER COLUMN [fecha_fin_prevista] DATETIME2 NULL;
END
GO

IF EXISTS (SELECT 1 FROM sys.columns
           WHERE Name = N'fecha_devolucion_real'
           AND Object_ID = Object_ID(N'dbo.prestamos')
           AND system_type_id = TYPE_ID('date'))
BEGIN
    ALTER TABLE [dbo].[prestamos] ALTER COLUMN [fecha_devolucion_real] DATETIME2 NULL;
END
GO

-- 3.3) Recrear índice sobre fecha_fin_prevista
IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name = 'IX_prestamo_fecha_fin'
               AND object_id = OBJECT_ID('dbo.prestamos'))
BEGIN
    CREATE NONCLUSTERED INDEX [IX_prestamo_fecha_fin]
        ON [dbo].[prestamos] ([fecha_fin_prevista]);
END
GO

-- =========================================================================
-- 4) Tickets: campos de incidencia y resolución
-- =========================================================================

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'tipo_incidencia'
               AND Object_ID = Object_ID(N'dbo.tickets'))
BEGIN
    ALTER TABLE [dbo].[tickets] ADD [tipo_incidencia] VARCHAR(50) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'asignado_a_id'
               AND Object_ID = Object_ID(N'dbo.tickets'))
BEGIN
    ALTER TABLE [dbo].[tickets] ADD [asignado_a_id] BIGINT NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'notas_resolucion'
               AND Object_ID = Object_ID(N'dbo.tickets'))
BEGIN
    ALTER TABLE [dbo].[tickets] ADD [notas_resolucion] VARCHAR(500) NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.columns
               WHERE Name = N'resuelto_en'
               AND Object_ID = Object_ID(N'dbo.tickets'))
BEGIN
    ALTER TABLE [dbo].[tickets] ADD [resuelto_en] DATETIME2 NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys
               WHERE name = 'FK_tickets_asignado_a')
BEGIN
    ALTER TABLE [dbo].[tickets]
        ADD CONSTRAINT [FK_tickets_asignado_a]
            FOREIGN KEY ([asignado_a_id])
            REFERENCES [dbo].[usuarios] ([id]) ON DELETE NO ACTION;
END
GO

-- =========================================================================
-- 5) Tabla usuarios_oficina (separación auth oficina vs campo)
-- =========================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables
               WHERE name = 'usuarios_oficina'
               AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE [dbo].[usuarios_oficina] (
        [id]               BIGINT        IDENTITY(1,1) NOT NULL,
        [username]         VARCHAR(80)   NOT NULL,
        [password]         VARCHAR(255)  NOT NULL,
        [email]            VARCHAR(150)  NULL,
        [rol]              VARCHAR(20)   NOT NULL,
        [activo]           BIT           NOT NULL DEFAULT 1,
        [refresh_token]    VARCHAR(500)  NULL,
        [estado_registro]  VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
        [creado_en]        DATETIME2     NOT NULL DEFAULT GETDATE(),
        [modificado_en]    DATETIME2     NULL,

        CONSTRAINT [PK_usuarios_oficina] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [UQ_usuarios_oficina_username] UNIQUE ([username]),
        CONSTRAINT [CK_usuarios_oficina_rol] CHECK ([rol] IN ('ADMIN', 'GESTOR', 'CONSULTA')),
        CONSTRAINT [CK_usuarios_oficina_estado] CHECK ([estado_registro] IN ('PENDIENTE', 'APROBADO', 'RECHAZADO'))
    );
END
GO

-- Seed admin oficina (si no existe ya)
IF NOT EXISTS (SELECT 1 FROM [dbo].[usuarios_oficina] WHERE [username] = 'admin')
BEGIN
    INSERT INTO [dbo].[usuarios_oficina] ([username], [password], [rol], [activo], [estado_registro])
    VALUES (
        'admin',
        '$2a$12$LJ3m4ys3uz0GHpcgMg/4Au1TeT0mk.dFRqkFKz8nUqY1S3Z3JXfMa',  -- BCrypt 'admin123'
        'ADMIN',
        1,
        'APROBADO'
    );
END
GO

-- =========================================================================
-- 6) Tabla usuarios_campo
-- =========================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables
               WHERE name = 'usuarios_campo'
               AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE [dbo].[usuarios_campo] (
        [id]               BIGINT        IDENTITY(1,1) NOT NULL,
        [username]         VARCHAR(80)   NOT NULL,
        [password]         VARCHAR(255)  NOT NULL,
        [telefono]         VARCHAR(20)   NULL,
        [nombre]           VARCHAR(100)  NULL,
        [apellidos]        VARCHAR(100)  NULL,
        [trabajador_id]    BIGINT        NULL,
        [activo]           BIT           NOT NULL DEFAULT 1,
        [refresh_token]    VARCHAR(500)  NULL,
        [estado_registro]  VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
        [creado_en]        DATETIME2     NOT NULL DEFAULT GETDATE(),
        [modificado_en]    DATETIME2     NULL,

        CONSTRAINT [PK_usuarios_campo] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [UQ_usuarios_campo_username] UNIQUE ([username]),
        CONSTRAINT [CK_usuarios_campo_estado] CHECK ([estado_registro] IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
        CONSTRAINT [FK_usuarios_campo_trabajador] FOREIGN KEY ([trabajador_id])
            REFERENCES [dbo].[trabajadores] ([id]) ON DELETE NO ACTION
    );
END
GO

-- =========================================================================
-- 7) Tabla tarifas
-- =========================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables
               WHERE name = 'tarifas'
               AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE [dbo].[tarifas] (
        [id]             BIGINT        IDENTITY(1,1) NOT NULL,
        [proveedor_id]   BIGINT        NOT NULL,
        [codigo_tarifa]  VARCHAR(50)   NULL,
        [vigente_desde]  DATE          NOT NULL,
        [vigente_hasta]  DATE          NULL,
        [observaciones]  VARCHAR(500)  NULL,
        [creado_en]      DATETIME2     NULL,
        [modificado_en]  DATETIME2     NULL,

        CONSTRAINT [PK_tarifas] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [FK_tarifas_proveedor] FOREIGN KEY ([proveedor_id])
            REFERENCES [dbo].[proveedores] ([id]) ON DELETE NO ACTION
    );
END
GO

-- =========================================================================
-- 8) Tabla tarifa_precios
-- =========================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables
               WHERE name = 'tarifa_precios'
               AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE [dbo].[tarifa_precios] (
        [id]                  BIGINT         IDENTITY(1,1) NOT NULL,
        [tarifa_id]           BIGINT         NOT NULL,
        [producto]            VARCHAR(100)   NOT NULL,
        [concepto_unificado]  VARCHAR(50)    NULL,
        [precio_sin_iva]      DECIMAL(8,3)   NOT NULL,
        [precio_con_iva]      DECIMAL(8,3)   NOT NULL,

        CONSTRAINT [PK_tarifa_precios] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [FK_tarifa_precios_tarifa] FOREIGN KEY ([tarifa_id])
            REFERENCES [dbo].[tarifas] ([id]) ON DELETE NO ACTION
    );
END
GO
