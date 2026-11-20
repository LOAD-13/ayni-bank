-- Libro de reclamaciones virtual (Ley N.º 29571, Codigo de Proteccion y Defensa del Consumidor,
-- y su reglamento). Cada hoja lleva un numero correlativo que no se reutiliza y el plazo de
-- respuesta, que se calcula al registrarla para que nadie lo recalcule distinto despues.

CREATE SEQUENCE reclamacion_numero_seq;

CREATE TABLE reclamacion (
    id                   UUID          PRIMARY KEY,
    numero               BIGINT        NOT NULL UNIQUE DEFAULT nextval('reclamacion_numero_seq'),
    tipo                 VARCHAR(7)    NOT NULL,
    nombre               VARCHAR(120)  NOT NULL,
    tipo_documento       VARCHAR(3)    NOT NULL,
    numero_documento     VARCHAR(12)   NOT NULL,
    correo               VARCHAR(160)  NOT NULL,
    telefono             VARCHAR(15),
    bien                 VARCHAR(9)    NOT NULL,
    descripcion_del_bien VARCHAR(140)  NOT NULL,
    monto_reclamado      NUMERIC(14,2),
    detalle              VARCHAR(2000) NOT NULL,
    pedido               VARCHAR(1000) NOT NULL,
    registrada_en        TIMESTAMPTZ   NOT NULL,
    responder_antes_de   DATE          NOT NULL,
    CONSTRAINT ck_reclamacion_tipo      CHECK (tipo IN ('RECLAMO', 'QUEJA')),
    CONSTRAINT ck_reclamacion_documento CHECK (tipo_documento IN ('DNI', 'CE')),
    CONSTRAINT ck_reclamacion_bien      CHECK (bien IN ('PRODUCTO', 'SERVICIO')),
    CONSTRAINT ck_reclamacion_monto     CHECK (monto_reclamado IS NULL OR monto_reclamado >= 0)
);

COMMENT ON TABLE reclamacion IS 'Hojas del libro de reclamaciones virtual. Contiene datos personales: no se registran en logs.';
COMMENT ON COLUMN reclamacion.tipo IS 'RECLAMO: disconformidad con el producto o servicio. QUEJA: con la atencion recibida.';
