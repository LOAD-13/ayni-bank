-- ═══════════════════════════════════════════════════════════════════════════
--  core · V5 · Devengo diario y capitalización mensual de intereses
--  HU-06 · AYNI-17 · ADR-0032
--
--  devengo_interes          Un registro por cuenta y día, con el saldo de cierre,
--                           la tasa aplicada y el importe con 8 decimales. Es la
--                           pista que permite auditar cada céntimo pagado.
--  capitalizacion_interes   Un registro por cuenta y mes: lo devengado, lo que se
--                           abonó (2 decimales) y la fracción de céntimo que pasa
--                           al mes siguiente para que no se pierda ni se regale.
--  cierre_diario            Qué días ya se procesaron. Si el proceso no corre un
--                           día, el siguiente arranque recupera los pendientes.
-- ═══════════════════════════════════════════════════════════════════════════

-- Las cuentas técnicas del banco no devengan: se marcan en lugar de reconocerlas
-- por su identificador en cada consulta.
ALTER TABLE cuenta ADD COLUMN tecnica BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE cuenta SET tecnica = TRUE
 WHERE id IN ('f0de0000-0000-4000-8000-000000000001', 'cce00000-0000-4000-8000-000000000001');

-- Cuenta técnica de gasto por intereses: la contrapartida de cada capitalización.
-- Su saldo negativo es el total de intereses que el banco ha pagado.
INSERT INTO cuenta (id, usuario_id, producto_id, numero, cci, moneda, estado, abierta_en, tecnica)
VALUES ('1e7e0000-0000-4000-8000-000000000001',
        '00000000-0000-0000-0000-000000000003',
        1,
        '00110000000003',
        '99900101000000000309',
        'PEN',
        'ACTIVA',
        now(),
        TRUE);

COMMENT ON TABLE cuenta IS
    'Identidad de la cuenta. El saldo NO vive aqui: es la suma de sus asientos. '
    'Cuentas tecnicas: f0de0000-... fondeo (V3), cce00000-... compensacion (V4) e 1e7e0000-... intereses (V5).';

CREATE TABLE devengo_interes (
    id                UUID          PRIMARY KEY,
    cuenta_id         UUID          NOT NULL REFERENCES cuenta (id),
    fecha             DATE          NOT NULL,
    saldo_base        NUMERIC(14,2) NOT NULL,
    tasa_producto_id  BIGINT        NOT NULL REFERENCES tasa_producto (id),
    -- Se copia la TEA aunque esté en tasa_producto: el registro tiene que leerse
    -- solo, sin depender de que nadie toque el catálogo después.
    tea               NUMERIC(9,6)  NOT NULL,
    importe           NUMERIC(19,8) NOT NULL,
    capitalizacion_id UUID,
    registrado_en     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_devengo_saldo   CHECK (saldo_base > 0),
    CONSTRAINT ck_devengo_importe CHECK (importe >= 0),
    CONSTRAINT ux_devengo_cuenta_fecha UNIQUE (cuenta_id, fecha)
);

CREATE INDEX ix_devengo_pendiente
    ON devengo_interes (cuenta_id, fecha)
    WHERE capitalizacion_id IS NULL;

COMMENT ON TABLE devengo_interes IS
    'interes_dia = saldo_cierre x ((1 + TEA)^(1/360) - 1), con BigDecimal y HALF_EVEN. Sin saldo no hay devengo.';

CREATE TABLE capitalizacion_interes (
    id                UUID          PRIMARY KEY,
    cuenta_id         UUID          NOT NULL REFERENCES cuenta (id),
    -- Primer día del mes capitalizado.
    periodo           DATE          NOT NULL,
    devengado         NUMERIC(19,8) NOT NULL,
    residuo_anterior  NUMERIC(19,8) NOT NULL,
    abonado           NUMERIC(14,2) NOT NULL,
    residuo           NUMERIC(19,8) NOT NULL,
    -- Nulo si lo acumulado no llegó a medio céntimo y no hubo nada que abonar.
    movimiento_id     UUID,
    registrado_en     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_capitalizacion_periodo CHECK (EXTRACT(DAY FROM periodo) = 1),
    CONSTRAINT ck_capitalizacion_abonado CHECK (abonado >= 0),
    CONSTRAINT ck_capitalizacion_cuadra  CHECK (devengado + residuo_anterior = abonado + residuo),
    CONSTRAINT ck_capitalizacion_movimiento CHECK ((abonado = 0) = (movimiento_id IS NULL)),
    CONSTRAINT ux_capitalizacion_cuenta_periodo UNIQUE (cuenta_id, periodo)
);

ALTER TABLE devengo_interes
    ADD CONSTRAINT fk_devengo_capitalizacion
    FOREIGN KEY (capitalizacion_id) REFERENCES capitalizacion_interes (id);

CREATE TABLE cierre_diario (
    fecha          DATE          PRIMARY KEY,
    cuentas        INTEGER       NOT NULL,
    devengado      NUMERIC(19,8) NOT NULL,
    procesado_en   TIMESTAMPTZ   NOT NULL
);

-- La pista de auditoría también registra cada capitalización.
ALTER TABLE evento_auditoria DROP CONSTRAINT ck_evento_auditoria_tipo;
ALTER TABLE evento_auditoria ADD CONSTRAINT ck_evento_auditoria_tipo
    CHECK (tipo IN ('TRANSFERENCIA_REALIZADA', 'DEPOSITO_SIMULADO',
                    'OPERACION_REPETIDA', 'OPERACION_RECHAZADA', 'INTERES_CAPITALIZADO'));
