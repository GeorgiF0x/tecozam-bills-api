-- =============================================================================
-- V37__vehiculos_matricula_filtered_unique.sql
-- Tecozam Bills Manager - Permitir reutilizar la matricula de un vehiculo
-- borrado logicamente
--
-- UQ_vehiculos_matricula (V3) es una UNIQUE constraint clasica: bloquea para
-- siempre la matricula de cualquier vehiculo borrado logicamente (eliminado_en
-- no nulo), por ejemplo un "vehiculo de sustitucion" autorregistrado por un
-- operario y despues rechazado por el gestor. Ni el alta manual en Admin Web,
-- ni el alta self-service desde la PWA, ni el import de Excel podian volver a
-- usar esa matricula, aunque el vehiculo ya no aparezca en ningun listado.
--
-- Solucion: mismo patron que V11 (trabajadores.email) — reemplazar la UNIQUE
-- constraint por un filtered unique index que solo aplica a los vehiculos
-- activos (eliminado_en IS NULL).
--
-- Sintaxis plana idempotente (sin BEGIN/END/GO): mismo patron seguro que
-- V23/V34/V35, que evita el fallo silencioso ya visto en produccion con
-- IF NOT EXISTS (...) BEGIN ... END GO.
-- =============================================================================

IF EXISTS (
    SELECT 1 FROM sys.key_constraints
    WHERE name = 'UQ_vehiculos_matricula'
      AND parent_object_id = OBJECT_ID('dbo.vehiculos')
)
    ALTER TABLE [dbo].[vehiculos] DROP CONSTRAINT [UQ_vehiculos_matricula];

-- Un filtered index exige QUOTED_IDENTIFIER ON en la sesion que lo crea;
-- sin esto el CREATE INDEX falla con el error 1934. El driver JDBC de SQL
-- Server lo deja ON por defecto, pero se fija explicito para no depender de
-- eso (verificado con sqlcmd, que por defecto lo trae OFF).
SET QUOTED_IDENTIFIER ON;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = 'UX_vehiculos_matricula_activo'
      AND object_id = OBJECT_ID('dbo.vehiculos')
)
    CREATE UNIQUE NONCLUSTERED INDEX [UX_vehiculos_matricula_activo]
        ON [dbo].[vehiculos]([matricula])
        WHERE [eliminado_en] IS NULL;
