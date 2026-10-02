-- HU-02 · Verificacion del DNI de punta a punta (AYNI-13, ADR-0028)
--
-- Tres cambios:
--
-- 1. Intentos por lado. El criterio de aceptacion de HU-02 dice "maximo 3 intentos de
--    captura POR LADO"; V6 contaba uno solo por solicitud. Se separan en anverso y reverso
--    y se retira el contador unico. Los intentos que ya hubiera se asignan al anverso: es
--    el primer paso del flujo, y ninguna solicitud en produccion los ha consumido todavia
--    (el flujo no estaba conectado).
--
-- 2. Bloqueo optimista. Dos fallos simultaneos del mismo lado leian el mismo contador y
--    escribian el mismo valor: uno de los dos se perdia. Con `version`, el segundo falla en
--    lugar de pisar al primero.
--
-- 3. Lecturas del DNI. Lo que leyo el OCR y lo que confirmo el titular, en filas distintas
--    con su fuente, para poder responder siempre a «¿este dato lo leyo una maquina o lo
--    escribio la persona?» (ADR-0009). El numero va cifrado con AES-256-GCM, igual que
--    documento_declarado.

ALTER TABLE solicitud_onboarding
    ADD COLUMN intentos_kyc_anverso SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN intentos_kyc_reverso SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE solicitud_onboarding SET intentos_kyc_anverso = intentos_verificacion_kyc;

ALTER TABLE solicitud_onboarding
    DROP CONSTRAINT ck_solicitud_intentos_kyc,
    DROP COLUMN intentos_verificacion_kyc;

ALTER TABLE solicitud_onboarding
    ADD CONSTRAINT ck_solicitud_intentos_kyc_anverso CHECK (intentos_kyc_anverso BETWEEN 0 AND 3),
    ADD CONSTRAINT ck_solicitud_intentos_kyc_reverso CHECK (intentos_kyc_reverso BETWEEN 0 AND 3);

COMMENT ON COLUMN solicitud_onboarding.intentos_kyc_anverso IS
    'Fotos del anverso rechazadas. Al llegar a 3, la solicitud pasa a EN_REVISION_MANUAL.';
COMMENT ON COLUMN solicitud_onboarding.intentos_kyc_reverso IS
    'Fotos del reverso rechazadas, o lecturas fallidas del OCR. Al llegar a 3, EN_REVISION_MANUAL.';

-- La ultima foto aceptada de cada lado es la que se usa: indice para encontrarla sin
-- recorrer todas las de la solicitud.
CREATE INDEX ix_documento_solicitud_tipo ON documento_kyc (solicitud_id, tipo_documento, subido_en DESC);

CREATE TABLE lectura_dni (
    id                UUID         PRIMARY KEY,
    solicitud_id      UUID         NOT NULL,
    fuente            VARCHAR(24)  NOT NULL,
    confiable         BOOLEAN      NOT NULL,
    -- Criptograma AES-256-GCM (prefijo v1:). Nunca el numero en claro.
    numero_cifrado    VARCHAR(255) NOT NULL,
    numero_ultimos4   VARCHAR(4)   NOT NULL,
    nombres           VARCHAR(80)  NOT NULL,
    apellidos         VARCHAR(120) NOT NULL,
    fecha_nacimiento  DATE         NOT NULL,
    sexo              CHAR(1)      NOT NULL,
    fecha_emision     DATE,
    leida_en          TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_lectura_solicitud
        FOREIGN KEY (solicitud_id) REFERENCES solicitud_onboarding (id) ON DELETE CASCADE,
    CONSTRAINT ck_lectura_fuente
        CHECK (fuente IN ('MRZ', 'HEURISTICA_ANVERSO', 'TITULAR')),
    CONSTRAINT ck_lectura_sexo
        CHECK (sexo IN ('M', 'F')),
    CONSTRAINT ck_lectura_ultimos4
        CHECK (numero_ultimos4 ~ '^[0-9]{4}$')
);

CREATE INDEX ix_lectura_solicitud ON lectura_dni (solicitud_id, leida_en DESC);

COMMENT ON TABLE lectura_dni IS
    'Datos del DNI leidos por OCR (fuente MRZ o HEURISTICA_ANVERSO) o confirmados por el '
    'titular (fuente TITULAR). Una fila por lectura; nunca se sobrescriben. Ver ADR-0028.';
