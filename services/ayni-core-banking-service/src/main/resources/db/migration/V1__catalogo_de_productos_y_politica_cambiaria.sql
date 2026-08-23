-- ═══════════════════════════════════════════════════════════════════════════
--  core · V1 · Catálogo de productos, tasas y política cambiaria
--
--  Alcance deliberado: solo datos de referencia. Las tablas del libro mayor
--  (cuenta, transaccion, asiento_contable, devengo_interes, outbox_event…)
--  llegan con la Historia de Usuario que las necesita. Una migración fusionada
--  no se edita nunca; adelantar el modelo contable sin la historia refinada
--  significa arrastrar el error para siempre.
--
--  Ver §2.3 (productos), §3.7.1 (política de tipo de cambio) y §4.4
--  (justificación de tasa_producto con vigencia) del documento de diseño.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE producto (
    id          SMALLINT     PRIMARY KEY,
    codigo      VARCHAR(32)  NOT NULL UNIQUE,
    nombre      VARCHAR(64)  NOT NULL,
    tipo        VARCHAR(24)  NOT NULL,
    moneda      CHAR(3)      NOT NULL,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_producto_tipo   CHECK (tipo IN ('AHORRO', 'TARJETA_DEBITO')),
    CONSTRAINT ck_producto_moneda CHECK (moneda IN ('PEN', 'USD'))
);

COMMENT ON COLUMN producto.moneda IS 'ISO 4217. Cada cuenta lleva su moneda; nunca se mezclan en un asiento.';

-- La tasa es una tabla con vigencia, no un campo en producto: si la tasa
-- cambia, los intereses ya devengados se calcularon con la anterior y deben
-- poder recalcularse y auditarse. Ver §4.4.
CREATE TABLE tasa_producto (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    producto_id     SMALLINT      NOT NULL REFERENCES producto(id),
    tea             NUMERIC(9,6)  NOT NULL,
    trea            NUMERIC(9,6)  NOT NULL,
    vigencia_desde  DATE          NOT NULL,
    vigencia_hasta  DATE,
    creado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_tasa_tea      CHECK (tea  >= 0 AND tea  <= 1),
    CONSTRAINT ck_tasa_trea     CHECK (trea >= 0 AND trea <= 1),
    CONSTRAINT ck_tasa_vigencia CHECK (vigencia_hasta IS NULL OR vigencia_hasta > vigencia_desde)
);

COMMENT ON COLUMN tasa_producto.tea  IS 'Tasa Efectiva Anual en tanto por uno. 0.045000 = 4.50 %.';
COMMENT ON COLUMN tasa_producto.trea IS 'Tasa de Rendimiento Efectivo Anual, de publicación obligatoria según la SBS. Al no cobrar mantenimiento, TREA = TEA.';

-- Una sola tasa vigente por producto en cada momento.
CREATE UNIQUE INDEX ux_tasa_producto_vigente
    ON tasa_producto (producto_id)
    WHERE vigencia_hasta IS NULL;

-- Tipo de cambio: se guarda la referencia oficial y el aplicado por Ayni.
-- Sin el registro del spread no se puede auditar por qué el cliente recibió
-- ese importe y no otro. Ver §3.7.1.
CREATE TABLE tipo_cambio (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha             DATE          NOT NULL,
    moneda_origen     CHAR(3)       NOT NULL,
    moneda_destino    CHAR(3)       NOT NULL,
    referencia_compra NUMERIC(19,6) NOT NULL,
    referencia_venta  NUMERIC(19,6) NOT NULL,
    fuente            VARCHAR(32)   NOT NULL,
    obtenido_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_tc_monedas  CHECK (moneda_origen <> moneda_destino),
    CONSTRAINT ck_tc_positivo CHECK (referencia_compra > 0 AND referencia_venta > 0),
    CONSTRAINT ck_tc_spread   CHECK (referencia_venta >= referencia_compra)
);

CREATE UNIQUE INDEX ux_tipo_cambio_dia
    ON tipo_cambio (fecha, moneda_origen, moneda_destino, fuente);

COMMENT ON TABLE tipo_cambio IS 'Referencia SUNAT/BCRP cacheada a diario. El tipo final aplicado a cada conversión se registra en la transacción, con su spread.';

-- Segmentos comerciales: el spread depende del saldo promedio del cliente.
CREATE TABLE segmento_cambiario (
    id                     SMALLINT     PRIMARY KEY,
    codigo                 VARCHAR(24)  NOT NULL UNIQUE,
    nombre                 VARCHAR(64)  NOT NULL,
    spread                 NUMERIC(9,6) NOT NULL,
    saldo_promedio_minimo  NUMERIC(19,4) NOT NULL,
    CONSTRAINT ck_segmento_spread CHECK (spread >= 0 AND spread <= 0.1)
);

COMMENT ON COLUMN segmento_cambiario.spread IS 'Margen total en tanto por uno, aplicado simétricamente: la mitad a compra y la mitad a venta.';

-- ─── Datos de referencia ───────────────────────────────────────────────────

INSERT INTO producto (id, codigo, nombre, tipo, moneda) VALUES
    (1, 'AHORRO_PEN',  'Cuenta Ayni Soles',     'AHORRO',        'PEN'),
    (2, 'AHORRO_USD',  'Cuenta Ayni Dólares',   'AHORRO',        'USD'),
    (3, 'DEBITO_PEN',  'Tarjeta Ayni Soles',    'TARJETA_DEBITO','PEN'),
    (4, 'DEBITO_USD',  'Tarjeta Ayni Dólares',  'TARJETA_DEBITO','USD');

-- TREA = TEA porque Ayni no cobra comisión de mantenimiento. Es el argumento
-- comercial del producto y la interfaz lo muestra explícitamente.
INSERT INTO tasa_producto (producto_id, tea, trea, vigencia_desde) VALUES
    (1, 0.045000, 0.045000, DATE '2026-08-17'),
    (2, 0.012000, 0.012000, DATE '2026-08-17');

INSERT INTO segmento_cambiario (id, codigo, nombre, spread, saldo_promedio_minimo) VALUES
    (1, 'ESTANDAR',     'Estándar',     0.005000, 0.0000),
    (2, 'PREFERENCIAL', 'Preferencial', 0.002500, 5000.0000);
