-- =============================================================================
-- V40__usuarios_campo_permiso_editar_tickets.sql
-- Tecozam Bills Manager - Permiso explicito para editar/eliminar tickets
-- propios
--
-- Hasta ahora, cualquier operario con sesion (ROLE_CAMPO) podia editar o
-- eliminar sus propios tickets mientras estuvieran en estado PENDIENTE
-- (ver TicketController.actualizarPropio/eliminarPropio), sin ninguna
-- restriccion adicional. El cliente pide restringirlo a un grupo concreto
-- de operarios, que un ADMIN concede explicitamente.
--
-- Por defecto NOT NULL DEFAULT 0: todos los operarios existentes pierden
-- la capacidad hasta que un ADMIN se la conceda de forma expresa (decision
-- explicita del cliente, no un descuido).
--
-- Sintaxis plana idempotente (sin BEGIN/END/GO), mismo patron seguro que
-- V23/V34/V35/V37/V38/V39.
-- =============================================================================

IF COL_LENGTH('dbo.usuarios_campo', 'puede_editar_tickets') IS NULL
    ALTER TABLE [dbo].[usuarios_campo] ADD [puede_editar_tickets] BIT NOT NULL CONSTRAINT DF_usuarios_campo_puede_editar_tickets DEFAULT 0;
