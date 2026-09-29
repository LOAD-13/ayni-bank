-- HU-22 · Segundo factor a elección y verificación del contacto en el registro (AYNI-126)
--
-- Tablas para soporte de múltiples métodos 2FA (TOTP, Correo, SMS) y desafíos por código OTP.

-- ─── metodo_segundo_factor ──────────────────────────────────────────────────
CREATE TABLE metodo_segundo_factor (
    id              UUID        PRIMARY KEY,
    usuario_id      UUID        NOT NULL,
    tipo            VARCHAR(30) NOT NULL,
    secreto         VARCHAR(255),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmado_en   TIMESTAMPTZ,

    CONSTRAINT fk_metodo_segundo_factor_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_metodo_segundo_factor_tipo
        CHECK (tipo IN ('APP_AUTENTICADORA', 'CORREO_ELECTRONICO', 'SMS')),
    CONSTRAINT uk_usuario_tipo_metodo
        UNIQUE (usuario_id, tipo)
);

COMMENT ON TABLE metodo_segundo_factor IS
    'Métodos de segundo factor registrados y su estado de confirmación por usuario.';

COMMENT ON COLUMN metodo_segundo_factor.secreto IS
    'Criptograma AES-256-GCM del secreto TOTP (si aplica). Nulo para tipo CORREO_ELECTRONICO o SMS.';

-- ─── desafio_por_codigo ─────────────────────────────────────────────────────
CREATE TABLE desafio_por_codigo (
    id                  UUID        PRIMARY KEY,
    usuario_id          UUID        NOT NULL,
    tipo_factor         VARCHAR(30) NOT NULL,
    hash_codigo         VARCHAR(255) NOT NULL,
    intentos_realizados SMALLINT    NOT NULL DEFAULT 0,
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_en           TIMESTAMPTZ NOT NULL,
    verificado_en       TIMESTAMPTZ,

    CONSTRAINT fk_desafio_por_codigo_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_desafio_por_codigo_intentos
        CHECK (intentos_realizados >= 0 AND intentos_realizados <= 3)
);

CREATE INDEX idx_desafio_por_codigo_usuario_expira
    ON desafio_por_codigo (usuario_id, expira_en);

COMMENT ON TABLE desafio_por_codigo IS
    'Desafíos de código OTP de un solo uso por correo/SMS con vigencia de 10 minutos y límite de 3 intentos.';

COMMENT ON COLUMN desafio_por_codigo.hash_codigo IS
    'Huella SHA-256 del código de 6 dígitos. El código en claro jamás se almacena.';
