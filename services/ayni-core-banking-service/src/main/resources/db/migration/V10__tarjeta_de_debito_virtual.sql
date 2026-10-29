-- HU-09 · Tarjeta de débito virtual (AYNI-20) · subtareas AYNI-235 a AYNI-240
--
-- Una tarjeta por titular, asociada a su cuenta de ahorro. Del PAN se guardan tres cosas y
-- ninguna permite leerlo sin la clave:
--   pan_cifrado   AES-256-GCM con IV aleatorio: dos tarjetas iguales dan textos distintos.
--   pan_huella    HMAC-SHA256: determinista, es lo que impone que el PAN sea único.
--   ultimos4      lo único que se muestra por defecto.
-- El CVV no tiene columna: se calcula cuando se pide y no se guarda nunca (PCI DSS).

CREATE TABLE tarjeta (
    id              UUID         PRIMARY KEY,
    cuenta_id       UUID         NOT NULL,
    usuario_id      UUID         NOT NULL,
    pan_cifrado     VARCHAR(120) NOT NULL,
    pan_huella      CHAR(64)     NOT NULL,
    ultimos4        CHAR(4)      NOT NULL,
    -- Último día del mes de vencimiento: cuatro años desde la emisión.
    vence_en        DATE         NOT NULL,
    estado          VARCHAR(16)  NOT NULL,
    emitida_en      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_tarjeta_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    CONSTRAINT ck_tarjeta_estado CHECK (estado IN ('ACTIVA', 'CONGELADA')),
    CONSTRAINT ck_tarjeta_ultimos4 CHECK (ultimos4 ~ '^[0-9]{4}$')
);

CREATE UNIQUE INDEX ux_tarjeta_pan_huella ON tarjeta (pan_huella);
-- Una sola tarjeta por titular: la emisión es idempotente también en la base.
CREATE UNIQUE INDEX ux_tarjeta_usuario ON tarjeta (usuario_id);

COMMENT ON COLUMN tarjeta.pan_cifrado IS
    'AES-256-GCM con prefijo de esquema v1:. Nunca en claro, nunca en logs.';

-- Los controles que el titular ajusta en HU-10: congelar, límite diario y compras por
-- internet. Nacen con la tarjeta, con valores por defecto prudentes.
CREATE TABLE control_tarjeta (
    tarjeta_id            UUID          PRIMARY KEY,
    limite_diario         NUMERIC(14,2) NOT NULL DEFAULT 500.00,
    compras_por_internet  BOOLEAN       NOT NULL DEFAULT TRUE,
    actualizado_en        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_control_tarjeta FOREIGN KEY (tarjeta_id) REFERENCES tarjeta (id),
    CONSTRAINT ck_control_limite_positivo CHECK (limite_diario > 0)
);
