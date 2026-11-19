-- =============================================================================
-- V38__vehiculos_matricula_nullable_industrial.sql
-- Tecozam Bills Manager - Permitir matricula NULL para maquinaria industrial
-- subcontratada (categoria INDUSTRIAL_MAQUINARIA), identificada por
-- codigo_obra en vez de matricula.
--
-- V3 creo vehiculos.matricula como NOT NULL (unico identificador en ese
-- momento). V10 anadio codigo_obra para maquinaria industrial, pero nunca
-- se relajo la restriccion NOT NULL de matricula: el alta self-service
-- desde la PWA (y el alta manual en Admin Web) de maquinaria sin matricula
-- fallaba con un error interno 500 al intentar el INSERT (violacion de la
-- restriccion NOT NULL a nivel de BD, aunque la entidad JPA ya declaraba
-- el campo como nullable).
--
-- El filtered unique index UX_vehiculos_matricula_activo (V37) debe excluir
-- tambien matricula IS NULL: igual que en V11 (trabajadores.email), SQL
-- Server solo permite UN valor NULL por indice unico (a diferencia del
-- estandar ANSI), asi que sin este ajuste la segunda maquinaria industrial
-- activa sin matricula violaria el indice.
--
-- Sintaxis plana idempotente (sin BEGIN/END/GO), mismo patron seguro que
-- V23/V34/V35/V37.
-- =============================================================================

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

-- Un filtered index exige QUOTED_IDENTIFIER ON en la sesion que lo crea;
-- sin esto el CREATE INDEX falla con el error 1934 (ver V37).
SET QUOTED_IDENTIFIER ON;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_vehiculos_matricula_activo'
      AND object_id = OBJECT_ID('dbo.vehiculos')
)
    CREATE UNIQUE NONCLUSTERED INDEX [UX_vehiculos_matricula_activo]
        ON [dbo].[vehiculos]([matricula])
        WHERE [eliminado_en] IS NULL AND [matricula] IS NOT NULL;
