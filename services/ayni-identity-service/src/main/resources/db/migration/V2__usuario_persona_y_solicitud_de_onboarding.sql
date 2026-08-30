-- HU-01 · Registro de usuario en Ayni Bank
--
-- Tres tablas que se llenan en momentos distintos del onboarding, y esa es la razon
-- de que sean tres y no una:
--
--   usuario                credenciales y estado. Se crea al registrarse.
--   persona                datos de identidad. Se llenan en HU-02, cuando el OCR lee
--                          el DNI. Hoy queda vacia a proposito.
--   solicitud_onboarding   el expediente que recorre el proceso. Es lo que devuelve el
--                          registro y lo que permite retomarlo donde se dejo.
--
-- Meterlo todo en `usuario` obligaria a dejar nulables los datos de identidad para
-- siempre, y a no poder distinguir «aun no lo sabemos» de «se borro».

-- ─── usuario ───────────────────────────────────────────────────────────────
CREATE TABLE usuario (
    id                  UUID         PRIMARY KEY,
    -- CITEXT seria lo natural, pero exige una extension que no todos los entornos
    -- gestionados permiten instalar. El dominio normaliza a minusculas antes de
    -- escribir, y el indice unico de mas abajo hace cumplir la unicidad de verdad.
    correo              VARCHAR(254) NOT NULL,
    celular             VARCHAR(9)   NOT NULL,
    -- Argon2id con los parametros de OWASP produce unos 100 caracteres. 255 deja
    -- margen para subir el coste sin migrar la columna.
    contrasena_hash     VARCHAR(255) NOT NULL,
    estado              VARCHAR(24)  NOT NULL,
    -- La Ley N.o 29733 no pide que el usuario acepte: pide poder demostrar que
    -- acepto, cuando y sobre que version de los terminos. Un booleano no prueba nada.
    consentimiento_en   TIMESTAMPTZ  NOT NULL,
    terminos_version    VARCHAR(16)  NOT NULL,
    registrado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_usuario_estado
        CHECK (estado IN ('PENDIENTE_VERIFICACION', 'ACTIVO', 'EN_REVISION', 'BLOQUEADO')),
    CONSTRAINT ck_usuario_celular
        CHECK (celular ~ '^9[0-9]{8}$'),
    -- El dominio ya valida el formato del correo. Esta comprobacion existe porque la
    -- base de datos tambien recibe escrituras de migraciones y de mantenimiento, y
    -- ahi no pasa nadie por el dominio.
    CONSTRAINT ck_usuario_correo
        CHECK (correo LIKE '%_@_%.__%'),
    CONSTRAINT ck_usuario_hash_argon2
        CHECK (contrasena_hash LIKE '$argon2%')
);

-- Unicidad sobre el correo ya normalizado. Es la restriccion que impide el usuario
-- duplicado del escenario 2 incluso si dos peticiones simultaneas superan a la vez la
-- comprobacion previa en memoria: la carrera la resuelve la base, no la aplicacion.
CREATE UNIQUE INDEX ux_usuario_correo ON usuario (correo);

COMMENT ON TABLE usuario IS
    'Credenciales y estado del usuario. Los datos de identidad viven en persona.';
COMMENT ON COLUMN usuario.contrasena_hash IS
    'Derivacion Argon2id. Jamas la contrasena en claro ni un hash reversible.';

-- ─── persona ───────────────────────────────────────────────────────────────
-- Se llena en HU-02, cuando el OCR extrae los datos del DNI y la persona los
-- confirma. Se crea ya para que el modelo este completo y la relacion exista desde
-- el principio: una migracion fusionada no se edita nunca.
CREATE TABLE persona (
    id                  UUID         PRIMARY KEY,
    usuario_id          UUID         NOT NULL,
    nombres             VARCHAR(80),
    apellido_paterno    VARCHAR(60),
    apellido_materno    VARCHAR(60),
    tipo_documento      VARCHAR(16),
    -- Cifrado AES-256-GCM en reposo, segun §5.2. Por eso es VARCHAR largo y no un
    -- campo de ocho digitos: lo que se guarda es el criptograma, no el numero.
    numero_documento    VARCHAR(255),
    -- Los ultimos cuatro digitos en claro permiten buscar y mostrar sin descifrar
    -- el registro entero ni exponer el documento completo.
    documento_ultimos4  VARCHAR(4),
    fecha_nacimiento    DATE,
    nacionalidad        VARCHAR(48),
    creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_persona_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_persona_tipo_documento
        CHECK (tipo_documento IS NULL OR tipo_documento IN ('DNI', 'CE', 'PASAPORTE'))
);

-- Una persona por usuario. Sin esto, un KYC reintentado crearia una segunda persona
-- y el sistema tendria dos identidades para el mismo titular.
CREATE UNIQUE INDEX ux_persona_usuario ON persona (usuario_id);

COMMENT ON COLUMN persona.numero_documento IS
    'Criptograma AES-256-GCM. Nunca se registra en logs ni se devuelve por la API.';

-- ─── solicitud_onboarding ──────────────────────────────────────────────────
CREATE TABLE solicitud_onboarding (
    id                  UUID         PRIMARY KEY,
    usuario_id          UUID,
    estado              VARCHAR(32)  NOT NULL,
    paso_actual         SMALLINT     NOT NULL DEFAULT 1,
    creada_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizada_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expira_en           TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_solicitud_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_solicitud_estado
        CHECK (estado IN ('INICIADA', 'DOCUMENTO_CARGADO', 'BIOMETRIA_PENDIENTE',
                          'APROBADA', 'EN_REVISION_MANUAL', 'RECHAZADA', 'EXPIRADA')),
    CONSTRAINT ck_solicitud_paso
        CHECK (paso_actual BETWEEN 1 AND 5)
);

CREATE INDEX ix_solicitud_usuario ON solicitud_onboarding (usuario_id);
CREATE INDEX ix_solicitud_estado ON solicitud_onboarding (estado);

-- `usuario_id` es nulable, y es deliberado. Cuando alguien intenta registrarse con un
-- correo que ya existe, el servicio devuelve una solicitud senuelo para que la
-- respuesta sea indistinguible de un registro real. Esa solicitud no apunta a ningun
-- usuario porque no se creo ninguno. Sin esta columna nulable, el senuelo obligaria a
-- inventar un usuario, que es justo lo que el escenario 2 prohibe. Ver ADR-0008.
COMMENT ON COLUMN solicitud_onboarding.usuario_id IS
    'Nulo en las solicitudes senuelo que devuelve el registro ante un correo ya '
    'existente. Ver ADR-0008.';
COMMENT ON TABLE solicitud_onboarding IS
    'Expediente de onboarding. Permite retomar el proceso donde se dejo.';
