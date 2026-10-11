-- HU-07 · Confirmacion de operaciones con el segundo factor (ADR-0031)

CREATE TABLE confirmacion_de_operacion (
    id                  UUID         PRIMARY KEY,
    usuario_id          UUID         NOT NULL,
    -- SHA-256 en Base64 de la operacion en forma canonica. identity no guarda a quien se
    -- transfiere ni cuanto: solo lo necesario para firmar un token que sirva para esa
    -- operacion y para ninguna otra.
    huella              VARCHAR(64)  NOT NULL,
    metodo              VARCHAR(30)  NOT NULL,
    -- El codigo enviado al correo; nulo con la app autenticadora.
    desafio_codigo_id   UUID,
    intentos_fallidos   SMALLINT     NOT NULL DEFAULT 0,
    creada_en           TIMESTAMPTZ  NOT NULL,
    expira_en           TIMESTAMPTZ  NOT NULL,
    usada_en            TIMESTAMPTZ,

    CONSTRAINT fk_confirmacion_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_confirmacion_metodo
        CHECK (metodo IN ('APP_AUTENTICADORA', 'CORREO_ELECTRONICO', 'SMS')),
    CONSTRAINT ck_confirmacion_intentos
        CHECK (intentos_fallidos BETWEEN 0 AND 3),
    CONSTRAINT ck_confirmacion_vigencia
        CHECK (expira_en > creada_en)
);

CREATE INDEX ix_confirmacion_usuario ON confirmacion_de_operacion (usuario_id, creada_en DESC);

-- ─── evento_auditoria: dos tipos nuevos ───────────────────────────────────
ALTER TABLE evento_auditoria DROP CONSTRAINT ck_evento_auditoria_tipo;
ALTER TABLE evento_auditoria ADD CONSTRAINT ck_evento_auditoria_tipo
    CHECK (tipo IN ('INGRESO_EXITOSO', 'CREDENCIALES_INVALIDAS',
                    'SEGUNDO_FACTOR_INVALIDO', 'INGRESO_BLOQUEADO',
                    'SEGUNDO_FACTOR_INSCRITO', 'SESION_RENOVADA',
                    'REUTILIZACION_DE_TOKEN',
                    'RECUPERACION_SOLICITADA', 'CONTRASENA_RESTABLECIDA',
                    'OPERACION_CONFIRMADA', 'CONFIRMACION_FALLIDA'));
