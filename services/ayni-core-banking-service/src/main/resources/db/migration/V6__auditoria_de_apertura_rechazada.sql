-- AYNI-326 · HU-05 escenario 2: el intento de abrir una segunda cuenta queda en la pista
--
-- La apertura duplicada se rechazaba sin dejar rastro: la transacción se deshacía entera y
-- no había forma de saber cuántas veces llegó una segunda aprobación para el mismo titular.
-- Se registra como un rechazo más, con su propio tipo y no como OPERACION_RECHAZADA: abrir
-- una cuenta no es una operación monetaria, y mezclarlo falsearía los rechazos de
-- depósitos y transferencias.

ALTER TABLE evento_auditoria DROP CONSTRAINT ck_evento_auditoria_tipo;
ALTER TABLE evento_auditoria ADD CONSTRAINT ck_evento_auditoria_tipo
    CHECK (tipo IN ('TRANSFERENCIA_REALIZADA', 'DEPOSITO_SIMULADO',
                    'OPERACION_REPETIDA', 'OPERACION_RECHAZADA', 'INTERES_CAPITALIZADO',
                    'APERTURA_RECHAZADA'));

-- Un rechazo, de operación o de apertura, siempre dice por qué y nunca tiene movimiento;
-- el resto, al revés.
ALTER TABLE evento_auditoria DROP CONSTRAINT ck_evento_auditoria_coherente;
ALTER TABLE evento_auditoria ADD CONSTRAINT ck_evento_auditoria_coherente
    CHECK ((tipo IN ('OPERACION_RECHAZADA', 'APERTURA_RECHAZADA')
                AND motivo IS NOT NULL AND movimiento_id IS NULL)
        OR (tipo NOT IN ('OPERACION_RECHAZADA', 'APERTURA_RECHAZADA')
                AND motivo IS NULL AND movimiento_id IS NOT NULL));
