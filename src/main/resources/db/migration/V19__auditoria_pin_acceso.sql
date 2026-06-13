-- =============================================================================
-- V19 — pin-biometric-view (task #47) — Auditoria de accesos al PIN.
--       Cada operacion sobre el PIN de tarjeta (guardado, reveal OK/FAILED)
--       deja registro con metodo (BIOMETRIA|PASSWORD), resultado, ip y
--       user_agent. Util para forense y revisiones de seguridad.
-- =============================================================================

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'auditoria_pin_acceso')
BEGIN
    CREATE TABLE [dbo].[auditoria_pin_acceso] (
        id                  BIGINT IDENTITY(1,1) NOT NULL,
        usuario_campo_id    BIGINT NOT NULL,
        tarjeta_id          BIGINT NOT NULL,
        metodo              NVARCHAR(20) NOT NULL,
        resultado           NVARCHAR(20) NOT NULL,
        ip                  VARCHAR(45) NOT NULL,
        user_agent          NVARCHAR(500) NOT NULL,
        [timestamp]         DATETIME2 NOT NULL CONSTRAINT DF_auditoria_pin_timestamp DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_auditoria_pin_acceso PRIMARY KEY (id),
        CONSTRAINT FK_auditoria_pin_usuario_campo
            FOREIGN KEY (usuario_campo_id) REFERENCES [dbo].[usuarios_campo](id),
        CONSTRAINT FK_auditoria_pin_tarjeta
            FOREIGN KEY (tarjeta_id) REFERENCES [dbo].[tarjetas](id),
        CONSTRAINT CK_auditoria_pin_metodo
            CHECK (metodo IN ('BIOMETRIA', 'PASSWORD', 'PIN_GUARDADO')),
        CONSTRAINT CK_auditoria_pin_resultado
            CHECK (resultado IN ('OK', 'FAILED'))
    );
END;
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_auditoria_pin_usuario_timestamp')
BEGIN
    CREATE INDEX IX_auditoria_pin_usuario_timestamp
        ON [dbo].[auditoria_pin_acceso](usuario_campo_id, [timestamp] DESC);
END;
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_auditoria_pin_tarjeta_timestamp')
BEGIN
    CREATE INDEX IX_auditoria_pin_tarjeta_timestamp
        ON [dbo].[auditoria_pin_acceso](tarjeta_id, [timestamp] DESC);
END;
GO
