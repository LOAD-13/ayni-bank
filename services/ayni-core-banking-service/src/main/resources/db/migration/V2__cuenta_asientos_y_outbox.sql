-- HU-05 · Apertura automática de cuenta de ahorro
--
-- Tres tablas, y el reparto entre ellas es la decisión de diseño principal:
--
--   cuenta    identidad de la cuenta: número, CCI, moneda, estado, producto.
--             NO guarda el saldo.
--   asiento   cada movimiento, por partida doble. El saldo es su suma.
--   outbox    eventos pendientes de publicar, escritos en el mismo COMMIT que
--             el hecho que los origina.
--
-- Que `cuenta` no tenga columna de saldo es deliberado y es el invariante que
-- sostiene todo lo demás. Un saldo almacenado es un dato que se puede editar, y
-- que tarde o temprano deja de cuadrar con los movimientos que lo produjeron;
-- cuando eso pasa en un banco, no hay forma de saber cuál de los dos miente.
-- Derivándolo de los asientos, la pregunta «¿por qué tengo este saldo?» siempre
-- tiene respuesta, y no existe ninguna operación capaz de cambiarlo sin dejar
-- rastro.

-- ─── cuenta ────────────────────────────────────────────────────────────────
CREATE TABLE cuenta (
    id                  UUID         PRIMARY KEY,
    -- Proyección local del usuario que vive en el schema `identity`. No hay clave
    -- foránea entre schemas a propósito: son bounded contexts distintos y una FK
    -- los acoplaría de por vida. Ver ADR-0004.
    usuario_id          UUID         NOT NULL,
    -- SMALLINT, como la clave de producto: el catalogo usa enteros cortos
    -- porque son cuatro filas fijas, no entidades que se creen en caliente.
    producto_id         SMALLINT     NOT NULL,
    numero              VARCHAR(14)  NOT NULL,
    -- Código de Cuenta Interbancario: 20 dígitos, con los dos últimos de control.
    cci                 VARCHAR(20)  NOT NULL,
    moneda              CHAR(3)      NOT NULL,
    estado              VARCHAR(16)  NOT NULL,
    abierta_en          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    cerrada_en          TIMESTAMPTZ,

    CONSTRAINT fk_cuenta_producto
        FOREIGN KEY (producto_id) REFERENCES producto (id),
    CONSTRAINT ck_cuenta_moneda
        CHECK (moneda IN ('PEN', 'USD')),
    CONSTRAINT ck_cuenta_estado
        CHECK (estado IN ('ACTIVA', 'BLOQUEADA', 'CERRADA')),
    CONSTRAINT ck_cuenta_numero
        CHECK (numero ~ '^[0-9]{14}$'),
    CONSTRAINT ck_cuenta_cci
        CHECK (cci ~ '^[0-9]{20}$')
);

CREATE UNIQUE INDEX ux_cuenta_numero ON cuenta (numero);
CREATE UNIQUE INDEX ux_cuenta_cci ON cuenta (cci);

-- Una sola cuenta de ahorro ACTIVA por titular y moneda. Es el criterio del
-- escenario 2 de HU-05, y se impone aquí y no solo en la aplicación porque dos
-- peticiones simultáneas pueden superar a la vez cualquier comprobación previa
-- en memoria: la carrera la resuelve la base, no el servicio.
CREATE UNIQUE INDEX ux_cuenta_titular_moneda
    ON cuenta (usuario_id, moneda)
    WHERE estado = 'ACTIVA';

COMMENT ON TABLE cuenta IS
    'Identidad de la cuenta. El saldo NO vive aqui: es la suma de sus asientos.';

-- ─── asiento ───────────────────────────────────────────────────────────────
CREATE TABLE asiento (
    id                  UUID         PRIMARY KEY,
    cuenta_id           UUID         NOT NULL,
    -- Agrupa los asientos de una misma operación. En una transferencia son dos:
    -- el cargo en una cuenta y el abono en la otra. La partida doble exige que la
    -- suma de un mismo movimiento sea siempre cero.
    movimiento_id       UUID         NOT NULL,
    tipo                VARCHAR(16)  NOT NULL,
    -- NUMERIC y nunca un tipo de coma flotante. `double` no puede representar 0.10
    -- exactamente, y en un sistema que acumula millones de operaciones ese error
    -- deja de ser teórico. Doce enteros y dos decimales cubren cualquier importe
    -- razonable en soles sin desbordar.
    importe             NUMERIC(14,2) NOT NULL,
    moneda              CHAR(3)      NOT NULL,
    concepto            VARCHAR(140) NOT NULL,
    registrado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_asiento_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    CONSTRAINT ck_asiento_tipo
        CHECK (tipo IN ('CARGO', 'ABONO')),
    CONSTRAINT ck_asiento_moneda
        CHECK (moneda IN ('PEN', 'USD')),
    -- El signo lo lleva el tipo, no el importe: un CARGO de -50 sería un abono
    -- disfrazado y rompería cualquier suma.
    CONSTRAINT ck_asiento_importe_positivo
        CHECK (importe > 0)
);

CREATE INDEX ix_asiento_cuenta ON asiento (cuenta_id, registrado_en DESC);
CREATE INDEX ix_asiento_movimiento ON asiento (movimiento_id);

COMMENT ON COLUMN asiento.importe IS
    'NUMERIC exacto. Los tipos de coma flotante estan prohibidos en importes.';

-- ─── outbox ────────────────────────────────────────────────────────────────
-- Patrón Transactional Outbox (ADR-0003). El evento se escribe en la MISMA
-- transacción que el hecho que lo origina, y un publicador aparte lo envía a
-- RabbitMQ después.
--
-- Publicar directamente desde dentro de la transacción parece más simple y tiene
-- un fallo que no se puede arreglar: si el envío sale bien y la transacción hace
-- rollback, se anunció una cuenta que no existe; si la transacción confirma y el
-- envío falla, la cuenta existe y nadie se entera. No hay orden de las dos
-- operaciones que evite las dos cosas, porque son dos sistemas distintos sin una
-- transacción común. Escribiendo el evento en la misma base, o pasan ambas o no
-- pasa ninguna. Es el escenario 4 de HU-05.
CREATE TABLE outbox (
    id                  UUID         PRIMARY KEY,
    -- Sobre qué agregado ocurrió. Permite reconstruir el orden por entidad.
    agregado_tipo       VARCHAR(32)  NOT NULL,
    agregado_id         UUID         NOT NULL,
    tipo_evento         VARCHAR(64)  NOT NULL,
    carga               JSONB        NOT NULL,
    creado_en           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    publicado_en        TIMESTAMPTZ,
    intentos            SMALLINT     NOT NULL DEFAULT 0,
    ultimo_error        TEXT,

    CONSTRAINT ck_outbox_intentos
        CHECK (intentos >= 0)
);

-- Índice parcial sobre lo pendiente. El publicador consulta constantemente «qué
-- falta por enviar», y sin el filtro esa consulta recorrería también todo el
-- histórico ya publicado, que crece sin parar.
CREATE INDEX ix_outbox_pendiente
    ON outbox (creado_en)
    WHERE publicado_en IS NULL;

COMMENT ON TABLE outbox IS
    'Eventos escritos en el mismo COMMIT que el hecho que los origina. ADR-0003.';
COMMENT ON COLUMN outbox.publicado_en IS
    'Nulo mientras el evento no haya salido a RabbitMQ.';

-- ─── operacion_idempotente ─────────────────────────────────────────────────
-- RabbitMQ garantiza que un mensaje llega «al menos una vez», no «exactamente una».
-- Un corte de red en el momento equivocado hace que el mismo evento se entregue dos
-- veces, y sin esta tabla eso serían dos cuentas para la misma persona.
--
-- La clave primaria ES la clave de idempotencia, y esa es toda la gracia: el segundo
-- INSERT falla por duplicado en lugar de necesitar un `SELECT` previo que otra
-- transacción concurrente podría adelantar.
CREATE TABLE operacion_idempotente (
    clave           UUID         PRIMARY KEY,
    resultado_id    UUID         NOT NULL,
    registrado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE operacion_idempotente IS
    'Toda operacion monetaria es idempotente. La clave primaria impone la unicidad.';

-- ─── secuencia del numero de cuenta ────────────────────────────────────────
-- El correlativo lo entrega la base y no la aplicación: garantizar que dos peticiones
-- simultáneas no obtengan el mismo número es justo lo que una secuencia sabe hacer y
-- un contador en memoria no. Arranca alto para que ningún número quede con ceros por
-- delante que la gente confunda al dictarlo por teléfono.
CREATE SEQUENCE secuencia_cuenta START WITH 1000000001 INCREMENT BY 1;
