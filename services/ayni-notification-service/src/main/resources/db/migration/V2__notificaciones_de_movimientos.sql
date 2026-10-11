-- ═══════════════════════════════════════════════════════════════════════════
--  notification · V2 · Notificaciones de movimientos (HU-13 · AYNI-24)
--
--  titular        Proyección local de los titulares activos. La alimenta el
--                 evento identity.titular.activado: este servicio no consulta el
--                 schema de identity ni le hace llamadas (ver diseño §4.5).
--  notificacion   Un aviso por titular afectado y por evento. La restricción
--                 UNIQUE es la que garantiza el escenario 2 de la historia: si
--                 el mismo evento llega dos veces, el segundo no inserta nada.
--  intento_envio  Cada intento queda registrado, salga bien o mal.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE titular (
    usuario_id      UUID          PRIMARY KEY,
    correo          VARCHAR(254)  NOT NULL,
    nombre_pila     VARCHAR(80)   NOT NULL DEFAULT '',
    actualizado_en  TIMESTAMPTZ   NOT NULL
);

COMMENT ON TABLE titular IS 'Proyección de identity: solo lo necesario para escribir al titular.';

CREATE TABLE notificacion (
    id                 UUID          PRIMARY KEY,
    evento_id          UUID          NOT NULL,
    usuario_id         UUID          NOT NULL,
    plantilla          VARCHAR(48)   NOT NULL,
    estado             VARCHAR(16)   NOT NULL,
    intentos           SMALLINT      NOT NULL DEFAULT 0,
    proximo_intento_en TIMESTAMPTZ   NOT NULL,
    datos              JSONB         NOT NULL,
    creada_en          TIMESTAMPTZ   NOT NULL,
    enviada_en         TIMESTAMPTZ,
    CONSTRAINT ck_notificacion_estado   CHECK (estado IN ('PENDIENTE', 'ENVIADA', 'FALLIDA')),
    CONSTRAINT ck_notificacion_intentos CHECK (intentos >= 0),
    -- La plantilla entra en la clave por si un mismo titular aparece en los dos
    -- lados de un evento: recibe el aviso de envío y el de recepción, no uno solo.
    CONSTRAINT ux_notificacion_evento UNIQUE (evento_id, usuario_id, plantilla)
);

-- El despachador solo recorre las pendientes cuyo turno ya llegó.
CREATE INDEX ix_notificacion_pendiente
    ON notificacion (proximo_intento_en)
    WHERE estado = 'PENDIENTE';

COMMENT ON COLUMN notificacion.datos IS 'Valores de la plantilla. Cuentas siempre enmascaradas; nunca PAN, contraseña, token ni documento.';

CREATE TABLE intento_envio (
    id               UUID          PRIMARY KEY,
    notificacion_id  UUID          NOT NULL REFERENCES notificacion (id),
    numero           SMALLINT      NOT NULL,
    resultado        VARCHAR(16)   NOT NULL,
    detalle          VARCHAR(500),
    ocurrido_en      TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_intento_resultado CHECK (resultado IN ('ENVIADO', 'ERROR')),
    CONSTRAINT ux_intento_numero UNIQUE (notificacion_id, numero)
);

-- ─── Plantillas ────────────────────────────────────────────────────────────
-- El escenario 1 exige importe, fecha y saldo resultante. Las plantillas de V1
-- nombraban al beneficiario y al remitente, un dato que core-banking no publica
-- y que no debe viajar en un correo: se muestra la cuenta enmascarada.

UPDATE plantilla
   SET cuerpo = 'Enviaste {{moneda}} {{importe}} desde tu cuenta {{cuenta}} a la cuenta {{contraparte}} el {{fecha}}. '
             || 'Tu saldo disponible es {{moneda}} {{saldo}}. Operación {{operacion}}.'
 WHERE id = 5;

UPDATE plantilla
   SET cuerpo = 'Recibiste {{moneda}} {{importe}} en tu cuenta {{cuenta}} desde la cuenta {{contraparte}} el {{fecha}}. '
             || 'Tu saldo disponible es {{moneda}} {{saldo}}. Operación {{operacion}}.'
 WHERE id = 6;

INSERT INTO plantilla (id, codigo, canal, asunto, cuerpo) VALUES
    (10, 'DEPOSITO_RECIBIDO', 'EMAIL',
        'Recibiste un depósito',
        'Recibiste un depósito de {{moneda}} {{importe}} en tu cuenta {{cuenta}} el {{fecha}}. '
            || 'Tu saldo disponible es {{moneda}} {{saldo}}. Operación {{operacion}}.');
