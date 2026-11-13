-- ═══════════════════════════════════════════════════════════════════════════
--  V13 · Conversion entre soles y dolares en tres pasos (HU-12, AYNI-271)
--
--  Un asiento nunca mezcla monedas. Por eso una conversion son tres piezas:
--    1. Salida en la moneda de origen: cargo al cliente, abono a la posicion
--       cambiaria del banco en esa moneda (un movimiento que cuadra a cero).
--    2. La conversion: esta fila, que une las dos patas y guarda el tipo de cambio
--       aplicado y cuando se obtuvo.
--    3. Entrada en la moneda de destino: cargo a la posicion cambiaria del banco en
--       esa moneda, abono al cliente (otro movimiento que cuadra a cero).
--  El saldo de las dos posiciones es la exposicion cambiaria del banco.
-- ═══════════════════════════════════════════════════════════════════════════

-- Titular reservado propio (…0004): ux_cuenta_titular_moneda admite una cuenta activa
-- por titular y moneda, y las dos posiciones son de monedas distintas.
INSERT INTO cuenta (id, usuario_id, producto_id, numero, cci, moneda, estado, abierta_en, tecnica)
VALUES ('ca4b0000-0000-4000-8000-000000000001', '00000000-0000-0000-0000-000000000004', 1,
        '00110000000004', '99900101000000000412', 'PEN', 'ACTIVA', now(), TRUE),
       ('ca4b0000-0000-4000-8000-000000000002', '00000000-0000-0000-0000-000000000004', 2,
        '00120000000004', '99900102000000000421', 'USD', 'ACTIVA', now(), TRUE);

CREATE TABLE conversion (
    id                    UUID          PRIMARY KEY,
    usuario_id            UUID          NOT NULL,
    movimiento_salida_id  UUID          NOT NULL UNIQUE,
    movimiento_entrada_id UUID          NOT NULL UNIQUE,
    moneda_origen         CHAR(3)       NOT NULL,
    importe_origen        NUMERIC(19,2) NOT NULL,
    moneda_destino        CHAR(3)       NOT NULL,
    importe_destino       NUMERIC(19,2) NOT NULL,
    -- Soles por dolar, ya con el spread del banco.
    tipo_aplicado         NUMERIC(19,6) NOT NULL,
    tipo_obtenido_en      TIMESTAMPTZ   NOT NULL,
    tipo_en_cache         BOOLEAN       NOT NULL,
    registrada_en         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_conversion_monedas CHECK (moneda_origen <> moneda_destino
        AND moneda_origen IN ('PEN', 'USD') AND moneda_destino IN ('PEN', 'USD')),
    CONSTRAINT ck_conversion_importes CHECK (importe_origen > 0 AND importe_destino > 0),
    CONSTRAINT ck_conversion_tipo CHECK (tipo_aplicado > 0)
);

CREATE INDEX ix_conversion_usuario ON conversion (usuario_id, registrada_en DESC);

COMMENT ON TABLE conversion IS
    'Une las dos patas de una conversion y guarda el tipo aplicado: con estos datos se recalcula el importe recibido (HU-12, escenario 3).';

ALTER TABLE evento_auditoria DROP CONSTRAINT ck_evento_auditoria_tipo;
ALTER TABLE evento_auditoria ADD CONSTRAINT ck_evento_auditoria_tipo
    CHECK (tipo IN ('TRANSFERENCIA_REALIZADA', 'DEPOSITO_SIMULADO',
                    'OPERACION_REPETIDA', 'OPERACION_RECHAZADA', 'INTERES_CAPITALIZADO',
                    'APERTURA_RECHAZADA', 'ACCESO_AJENO_DENEGADO',
                    'INTERBANCARIA_ENVIADA', 'INTERBANCARIA_COMPLETADA', 'INTERBANCARIA_REVERTIDA',
                    'TARJETA_EMITIDA', 'PAN_REVELADO',
                    'TARJETA_CONGELADA', 'TARJETA_DESCONGELADA',
                    'CONTROL_TARJETA_ACTUALIZADO', 'CONSUMO_TARJETA_REALIZADO',
                    'CONVERSION_REALIZADA'));
