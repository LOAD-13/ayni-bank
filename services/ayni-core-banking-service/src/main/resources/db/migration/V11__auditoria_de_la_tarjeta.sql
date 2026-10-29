-- HU-09 · La tarjeta en la pista de auditoría (AYNI-243)
--
-- El escenario 2 exige registrar la revelación del PAN «con fecha, hora e IP». La pista ya
-- tenía la fecha y la hora; se añade la IP, nula en los eventos que no la necesitan. Los dos
-- tipos nuevos llevan como recurso la tarjeta (en movimiento_id) y ningún motivo, así que la
-- regla de coherencia de la V6 ya los admite.

ALTER TABLE evento_auditoria ADD COLUMN ip VARCHAR(45);

ALTER TABLE evento_auditoria DROP CONSTRAINT ck_evento_auditoria_tipo;
ALTER TABLE evento_auditoria ADD CONSTRAINT ck_evento_auditoria_tipo
    CHECK (tipo IN ('TRANSFERENCIA_REALIZADA', 'DEPOSITO_SIMULADO',
                    'OPERACION_REPETIDA', 'OPERACION_RECHAZADA', 'INTERES_CAPITALIZADO',
                    'APERTURA_RECHAZADA', 'ACCESO_AJENO_DENEGADO',
                    'INTERBANCARIA_ENVIADA', 'INTERBANCARIA_COMPLETADA', 'INTERBANCARIA_REVERTIDA',
                    'TARJETA_EMITIDA', 'PAN_REVELADO'));

COMMENT ON COLUMN evento_auditoria.ip IS
    'IP de origen (X-Forwarded-For); hoy solo en PAN_REVELADO.';
