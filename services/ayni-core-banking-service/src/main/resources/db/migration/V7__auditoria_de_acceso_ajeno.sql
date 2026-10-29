-- AYNI-328 · HU-08 escenario 2: pedir un movimiento de otra persona queda en la pista
--
-- La consulta del comprobante ya negaba el acceso a un movimiento ajeno, pero no dejaba
-- rastro: no había forma de saber si alguien estaba probando identificadores. Se registra
-- con el movimiento que se intentó leer y sin motivo, así que la regla de coherencia de la
-- V6 ya lo admite; solo hay que añadir el tipo.

ALTER TABLE evento_auditoria DROP CONSTRAINT ck_evento_auditoria_tipo;
ALTER TABLE evento_auditoria ADD CONSTRAINT ck_evento_auditoria_tipo
    CHECK (tipo IN ('TRANSFERENCIA_REALIZADA', 'DEPOSITO_SIMULADO',
                    'OPERACION_REPETIDA', 'OPERACION_RECHAZADA', 'INTERES_CAPITALIZADO',
                    'APERTURA_RECHAZADA', 'ACCESO_AJENO_DENEGADO'));
