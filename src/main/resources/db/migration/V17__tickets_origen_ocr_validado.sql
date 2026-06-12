-- FLEET-01 (backend): el flujo /ocr-validado guarda los tickets con
-- origen = 'OCR_VALIDADO' para distinguirlos del OCR puro. El CHECK
-- original sólo permitía ('OCR', 'MANUAL'), bloqueando el INSERT.
--
-- Ampliamos el constraint para aceptar el nuevo valor sin afectar a los
-- registros existentes (no se reescribe ningún dato).

ALTER TABLE tickets DROP CONSTRAINT CK_tickets_origen;

ALTER TABLE tickets
    ADD CONSTRAINT CK_tickets_origen
    CHECK (origen IN ('OCR', 'MANUAL', 'OCR_VALIDADO'));
