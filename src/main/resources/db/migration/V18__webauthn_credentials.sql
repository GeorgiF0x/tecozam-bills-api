-- =============================================================================
-- V18 — pin-biometric-view — Credenciales FIDO2 / WebAuthn por usuario CAMPO.
--       Soporta enrolamiento de una credencial biometrica del dispositivo
--       movil del operario. v1 permite UNA credencial activa por usuario
--       (UNIQUE filtered: WHERE eliminado_en IS NULL) para poder ampliar a
--       multiples credenciales en v1.1 sin migracion destructiva.
-- =============================================================================

IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'webauthn_credentials')
BEGIN
    CREATE TABLE [dbo].[webauthn_credentials] (
        id                  BIGINT IDENTITY(1,1) NOT NULL,
        usuario_campo_id    BIGINT NOT NULL,
        credential_id       VARBINARY(255) NOT NULL,
        public_key          VARBINARY(MAX) NOT NULL,
        signature_count     BIGINT NOT NULL CONSTRAINT DF_webauthn_credentials_count DEFAULT 0,
        aaguid              BINARY(16) NULL,
        nombre_dispositivo  NVARCHAR(100) NULL,
        creado_en           DATETIME2 NOT NULL CONSTRAINT DF_webauthn_credentials_creado_en DEFAULT SYSUTCDATETIME(),
        modificado_en       DATETIME2 NULL,
        eliminado_en        DATETIME2 NULL,
        CONSTRAINT PK_webauthn_credentials PRIMARY KEY (id),
        CONSTRAINT FK_webauthn_credentials_usuario_campo
            FOREIGN KEY (usuario_campo_id) REFERENCES [dbo].[usuarios_campo](id)
    );
END;
GO

-- UNIQUE filtered: una credencial activa por usuario (permite v1.1 sin DROP)
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'UX_webauthn_credentials_usuario_activa')
BEGIN
    CREATE UNIQUE INDEX UX_webauthn_credentials_usuario_activa
        ON [dbo].[webauthn_credentials](usuario_campo_id)
        WHERE eliminado_en IS NULL;
END;
GO

-- Indice para busqueda por credential_id en el flujo de assertion
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_webauthn_credentials_credential_id')
BEGIN
    CREATE INDEX IX_webauthn_credentials_credential_id
        ON [dbo].[webauthn_credentials](credential_id)
        WHERE eliminado_en IS NULL;
END;
GO
