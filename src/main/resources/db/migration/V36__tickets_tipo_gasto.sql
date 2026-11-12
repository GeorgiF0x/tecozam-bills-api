IF COL_LENGTH('dbo.tickets', 'tipo_gasto') IS NULL
    ALTER TABLE [dbo].[tickets] ADD [tipo_gasto] NVARCHAR(20) NOT NULL CONSTRAINT DF_tickets_tipo_gasto DEFAULT 'COMBUSTIBLE';
