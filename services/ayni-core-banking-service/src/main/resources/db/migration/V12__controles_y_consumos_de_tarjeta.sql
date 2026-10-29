-- HU-10 · Control de la tarjeta (AYNI-21) · subtareas AYNI-246 a AYNI-251
--
-- Registra los consumos efectuados con la tarjeta para calcular lo consumido en el día
-- (en hora de Lima) frente al límite diario configurado por el titular, y amplía la pista
-- de auditoría con los eventos de congelamiento, descongelamiento, cambio de controles y
-- consumo con tarjeta.

CREATE TABLE consumo_tarjeta (
    id              UUID          PRIMARY KEY,
    tarjeta_id      UUID          NOT NULL,
    movimiento_id   UUID          NOT NULL,
    importe         NUMERIC(14,2) NOT NULL,
    moneda          CHAR(3)       NOT NULL,
    por_internet    BOOLEAN       NOT NULL,
    comercio        VARCHAR(120)  NOT NULL,
    registrado_en   TIMESTAMPTZ   NOT NULL,

    CONSTRAINT fk_consumo_tarjeta FOREIGN KEY (tarjeta_id) REFERENCES tarjeta (id),
    CONSTRAINT ck_consumo_tarjeta_importe CHECK (importe > 0)
);

CREATE INDEX ix_consumo_tarjeta_dia ON consumo_tarjeta (tarjeta_id, registrado_en DESC);

ALTER TABLE evento_auditoria DROP CONSTRAINT ck_evento_auditoria_tipo;
ALTER TABLE evento_auditoria ADD CONSTRAINT ck_evento_auditoria_tipo
    CHECK (tipo IN ('TRANSFERENCIA_REALIZADA', 'DEPOSITO_SIMULADO',
                    'OPERACION_REPETIDA', 'OPERACION_RECHAZADA', 'INTERES_CAPITALIZADO',
                    'APERTURA_RECHAZADA', 'ACCESO_AJENO_DENEGADO',
                    'INTERBANCARIA_ENVIADA', 'INTERBANCARIA_COMPLETADA', 'INTERBANCARIA_REVERTIDA',
                    'TARJETA_EMITIDA', 'PAN_REVELADO',
                    'TARJETA_CONGELADA', 'TARJETA_DESCONGELADA',
                    'CONTROL_TARJETA_ACTUALIZADO', 'CONSUMO_TARJETA_REALIZADO'));
