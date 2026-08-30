-- HU-04 · Inicio de sesión seguro con segundo factor
--
-- Cinco tablas, y ninguna de ellas guarda un secreto en claro:
--
--   segundo_factor          el secreto TOTP, cifrado con AES-256-GCM
--   control_de_acceso       intentos fallidos y hasta cuándo está pausado el ingreso
--   desafio_segundo_factor  el vale de dos minutos entre las dos pantallas del login
--   refresh_token           tokens de renovación por familia, guardados como huella
--   evento_auditoria        la pista que exige el criterio de aceptación

-- ─── segundo_factor ────────────────────────────────────────────────────────
CREATE TABLE segundo_factor (
    usuario_id      UUID         PRIMARY KEY,
    -- Criptograma AES-256-GCM del secreto Base32. En claro sería tan grave como
    -- guardar contraseñas: quien lo tenga genera códigos válidos indefinidamente.
    -- No se puede derivar como la contraseña porque el servidor necesita el valor
    -- original para calcular el código y compararlo.
    secreto         VARCHAR(255) NOT NULL,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Nulo mientras el usuario no haya tecleado un código válido. Sin ese paso, quien
    -- cierra la pantalla antes de escanear el QR se quedaría con un segundo factor
    -- activo que ninguna aplicación puede generar: sin acceso y sin salida.
    confirmado_en   TIMESTAMPTZ,

    CONSTRAINT fk_segundo_factor_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
);

COMMENT ON COLUMN segundo_factor.secreto IS
    'Criptograma AES-256-GCM del secreto TOTP. Jamas en claro, jamas en un log.';

-- ─── control_de_acceso ─────────────────────────────────────────────────────
-- Aparte de `usuario` a propósito: esta fila se escribe en cada intento fallido, y no
-- tiene sentido tocar el registro de identidad de alguien cada vez que un atacante
-- prueba una contraseña.
CREATE TABLE control_de_acceso (
    usuario_id          UUID        PRIMARY KEY,
    fallos_consecutivos SMALLINT    NOT NULL DEFAULT 0,
    -- Nulo mientras no haya bloqueo. El retardo es progresivo: se duplica con cada
    -- fallo a partir del sexto, con un techo de una hora para que nadie pueda dejar
    -- fuera al titular indefinidamente fallando a propósito.
    bloqueado_hasta     TIMESTAMPTZ,
    actualizado_en      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_control_acceso_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_control_acceso_fallos
        CHECK (fallos_consecutivos >= 0)
);

-- ─── desafio_segundo_factor ────────────────────────────────────────────────
CREATE TABLE desafio_segundo_factor (
    id              UUID        PRIMARY KEY,
    usuario_id      UUID        NOT NULL,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_en       TIMESTAMPTZ NOT NULL,
    -- Un desafío se canjea una sola vez. Sin esta marca, el mismo vale serviría para
    -- abrir tantas sesiones como se quisiera dentro de su ventana de dos minutos.
    consumido_en    TIMESTAMPTZ,

    CONSTRAINT fk_desafio_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
);

CREATE INDEX ix_desafio_expira ON desafio_segundo_factor (expira_en);

-- ─── refresh_token ─────────────────────────────────────────────────────────
CREATE TABLE refresh_token (
    id              UUID         PRIMARY KEY,
    -- Todos los tokens que salen de un mismo inicio de sesión comparten familia. Es lo
    -- que permite tirar la sesión entera de un golpe cuando se detecta una copia.
    familia_id      UUID         NOT NULL,
    usuario_id      UUID         NOT NULL,
    -- Huella SHA-256 en Base64. El token en claro solo existe en el navegador: una base
    -- filtrada no entrega sesiones activas.
    huella          VARCHAR(64)  NOT NULL,
    emitido_en      TIMESTAMPTZ  NOT NULL,
    expira_en       TIMESTAMPTZ  NOT NULL,
    -- Se rellena al rotar. Que llegue un token con esta columna ya puesta significa que
    -- hay dos copias en circulación: es la detección del escenario 4.
    consumido_en    TIMESTAMPTZ,
    -- Se rellena cuando la familia entera cae por seguridad.
    invalidado_en   TIMESTAMPTZ,

    CONSTRAINT fk_refresh_token_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
);

-- La huella tiene que ser única: es por donde se busca el token que llega del navegador.
CREATE UNIQUE INDEX ux_refresh_token_huella ON refresh_token (huella);
CREATE INDEX ix_refresh_token_familia ON refresh_token (familia_id);
CREATE INDEX ix_refresh_token_expira ON refresh_token (expira_en);

COMMENT ON COLUMN refresh_token.huella IS
    'SHA-256 en Base64 del token. El valor en claro nunca se persiste.';

-- ─── evento_auditoria ──────────────────────────────────────────────────────
-- HU-04 exige registrar todo ingreso, exitoso o fallido, con IP y agente de usuario.
-- Es una tabla y no un log de aplicación: un log rota, se trunca y lo lee cualquiera
-- con acceso a Loki. La IP es dato personal, y esta pista tiene finalidad, retención y
-- control de acceso propios, que es lo que espera la Ley N.o 29733.
CREATE TABLE evento_auditoria (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo            VARCHAR(32)  NOT NULL,
    -- Nulo cuando el correo no corresponde a ninguna cuenta. Registrar el intento
    -- importa aunque no se sepa contra quién iba.
    usuario_id      UUID,
    ip              VARCHAR(45)  NOT NULL,
    agente_usuario  VARCHAR(255) NOT NULL,
    ocurrido_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_evento_auditoria_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE SET NULL,
    CONSTRAINT ck_evento_auditoria_tipo
        CHECK (tipo IN ('INGRESO_EXITOSO', 'CREDENCIALES_INVALIDAS',
                        'SEGUNDO_FACTOR_INVALIDO', 'INGRESO_BLOQUEADO',
                        'SEGUNDO_FACTOR_INSCRITO', 'SESION_RENOVADA',
                        'REUTILIZACION_DE_TOKEN'))
);

CREATE INDEX ix_evento_auditoria_usuario ON evento_auditoria (usuario_id, ocurrido_en DESC);
CREATE INDEX ix_evento_auditoria_tipo ON evento_auditoria (tipo, ocurrido_en DESC);

COMMENT ON COLUMN evento_auditoria.ip IS
    'Dato personal segun la Ley N.o 29733. Vive aqui y no en los logs de aplicacion.';
-- 45 caracteres es lo que ocupa una IPv6 con sufijo IPv4 embebido, que es el caso mas
-- largo que puede llegar.
