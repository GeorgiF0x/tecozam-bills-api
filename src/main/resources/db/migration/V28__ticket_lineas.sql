-- =============================================================================
-- V28 — tabla ticket_lineas: un Ticket podra tener N lineas (una por foto),
-- en vez de los campos escalares planos actuales (1 ticket = 1 foto). Los
-- campos escalares de Ticket se mantienen por ahora (coexisten) hasta que
-- el flujo de creacion multi-foto (T3 de odd/tasks/ticket-multi-foto.md) los
-- sustituya. Idempotente, mismo patron que V26.
-- =============================================================================

IF NOT EXISTS (SELECT 1 FROM sys.tables
               WHERE name = 'ticket_lineas'
               AND schema_id = SCHEMA_ID('dbo'))
BEGIN
    CREATE TABLE [dbo].[ticket_lineas] (
        [id]            BIGINT         IDENTITY(1,1) NOT NULL,
        [ticket_id]     BIGINT         NOT NULL,
        [estacion]      NVARCHAR(255)  NULL,
        [fecha_hora]    DATETIME2      NULL,
        [producto]      NVARCHAR(255)  NULL,
        [num_recibo]    NVARCHAR(255)  NULL,
        [matricula]     NVARCHAR(255)  NULL,
        [kms]           INT            NULL,
        [litros]        DECIMAL(10,2)  NULL,
        [precio_litro]  DECIMAL(8,3)   NULL,
        [importe_total] DECIMAL(10,2)  NOT NULL,
        [foto_indice]   INT            NULL,

        CONSTRAINT [PK_ticket_lineas] PRIMARY KEY CLUSTERED ([id]),
        CONSTRAINT [FK_ticket_lineas_ticket] FOREIGN KEY ([ticket_id])
            REFERENCES [dbo].[tickets] ([id])
    );

    CREATE INDEX [IX_ticket_lineas_ticket_id] ON [dbo].[ticket_lineas] ([ticket_id]);
END
