IF COL_LENGTH('dbo.vehiculos', 'creado_por_campo') IS NULL
    ALTER TABLE [dbo].[vehiculos] ADD [creado_por_campo] BIT NOT NULL CONSTRAINT DF_vehiculos_creado_por_campo DEFAULT 0;
