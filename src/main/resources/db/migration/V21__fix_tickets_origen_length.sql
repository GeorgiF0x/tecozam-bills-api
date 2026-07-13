-- V17 amplió el CHECK constraint de tickets.origen para aceptar 'OCR_VALIDADO'
-- pero no amplió la columna, que seguía en VARCHAR(10). 'OCR_VALIDADO' tiene
-- 12 caracteres, por lo que todo insert con ese valor fallaba con
-- "String or binary data would be truncated" (SQL Error 8152).

ALTER TABLE [dbo].[tickets] ALTER COLUMN [origen] VARCHAR(20) NOT NULL;
