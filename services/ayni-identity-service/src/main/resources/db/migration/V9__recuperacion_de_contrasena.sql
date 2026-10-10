-- HU-21 · Recuperacion de la contrasena
--
-- Una tabla nueva y dos tipos nuevos en la pista de auditoria. Ver ADR-0030.

-- ─── token_recuperacion ────────────────────────────────────────────────────
CREATE TABLE token_recuperacion (
    id              UUID         PRIMARY KEY,
    usuario_id      UUID         NOT NULL,
    -- Huella SHA-256 en Base64, como en refresh_token. El enlace en claro solo existe en
    -- el correo: una base filtrada no permite cambiar contrasenas ajenas.
    huella          VARCHAR(64)  NOT NULL,
    emitido_en      TIMESTAMPTZ  NOT NULL,
    -- Treinta minutos despues de emitirse.
    expira_en       TIMESTAMPTZ  NOT NULL,
    -- Un enlace se usa una sola vez.
    usado_en        TIMESTAMPTZ,
    -- Lo anula un enlace posterior o el propio cambio de contrasena: solo vale el ultimo
    -- que llego al correo.
    anulado_en      TIMESTAMPTZ,

    CONSTRAINT fk_token_recuperacion_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_token_recuperacion_vigencia
        CHECK (expira_en > emitido_en)
);

CREATE UNIQUE INDEX ux_token_recuperacion_huella ON token_recuperacion (huella);
-- El freno de tres enlaces por hora cuenta por usuario y fecha de emision.
CREATE INDEX ix_token_recuperacion_usuario ON token_recuperacion (usuario_id, emitido_en DESC);

COMMENT ON COLUMN token_recuperacion.huella IS
    'SHA-256 en Base64 del token del enlace. El valor en claro nunca se persiste.';

-- ─── evento_auditoria: dos tipos nuevos ───────────────────────────────────
ALTER TABLE evento_auditoria DROP CONSTRAINT ck_evento_auditoria_tipo;
ALTER TABLE evento_auditoria ADD CONSTRAINT ck_evento_auditoria_tipo
    CHECK (tipo IN ('INGRESO_EXITOSO', 'CREDENCIALES_INVALIDAS',
                    'SEGUNDO_FACTOR_INVALIDO', 'INGRESO_BLOQUEADO',
                    'SEGUNDO_FACTOR_INSCRITO', 'SESION_RENOVADA',
                    'REUTILIZACION_DE_TOKEN',
                    'RECUPERACION_SOLICITADA', 'CONTRASENA_RESTABLECIDA'));
