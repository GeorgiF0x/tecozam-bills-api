-- =============================================================================
-- V16 — Actualizar CHECK constraint de prestamos.tipo_prestamo para que coincida
--       con el enum TipoPrestamo actual (TEMPORAL / INDEFINIDO). El constraint
--       anterior aceptaba HASTA_REPOSICION / CON_FECHA_FIN, valores que el
--       backend ya no envía — provocando HTTP 500 al crear cualquier préstamo
--       desde el admin web.
-- =============================================================================

IF EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = 'CK_prestamos_tipo_prestamo'
)
BEGIN
    ALTER TABLE [dbo].[prestamos] DROP CONSTRAINT [CK_prestamos_tipo_prestamo];
END;
GO

ALTER TABLE [dbo].[prestamos]
    ADD CONSTRAINT [CK_prestamos_tipo_prestamo]
    CHECK ([tipo_prestamo] IN ('TEMPORAL', 'INDEFINIDO'));
GO
