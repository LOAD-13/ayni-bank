-- ═══════════════════════════════════════════════════════════════════════════
--  V20 · La pista registra los intentos de escribir sin permiso (HU-15, escenario 3)
--
--  Un usuario sin el permiso de una operacion de escritura (por ejemplo, un
--  auditor) que la intenta queda en la pista: ESCRITURA_DENEGADA, con el permiso
--  que le falto en `motivo` y sin recurso de movimiento.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE evento_auditoria DROP CONSTRAINT ck_evento_auditoria_tipo;
ALTER TABLE evento_auditoria ADD CONSTRAINT ck_evento_auditoria_tipo
    CHECK (tipo IN ('TRANSFERENCIA_REALIZADA', 'DEPOSITO_SIMULADO',
                    'OPERACION_REPETIDA', 'OPERACION_RECHAZADA', 'INTERES_CAPITALIZADO',
                    'APERTURA_RECHAZADA', 'ACCESO_AJENO_DENEGADO',
                    'INTERBANCARIA_ENVIADA', 'INTERBANCARIA_COMPLETADA', 'INTERBANCARIA_REVERTIDA',
                    'TARJETA_EMITIDA', 'PAN_REVELADO',
                    'TARJETA_CONGELADA', 'TARJETA_DESCONGELADA',
                    'CONTROL_TARJETA_ACTUALIZADO', 'CONSUMO_TARJETA_REALIZADO',
                    'CONVERSION_REALIZADA',
                    'BLOQUEO_SOLICITADO', 'CUENTA_BLOQUEADA', 'CUATRO_OJOS_DENEGADO',
                    'ESCRITURA_DENEGADA'));

ALTER TABLE evento_auditoria DROP CONSTRAINT ck_evento_auditoria_coherente;
ALTER TABLE evento_auditoria ADD CONSTRAINT ck_evento_auditoria_coherente
    CHECK ((tipo IN ('OPERACION_RECHAZADA', 'APERTURA_RECHAZADA', 'ESCRITURA_DENEGADA')
                AND motivo IS NOT NULL AND movimiento_id IS NULL)
        OR (tipo NOT IN ('OPERACION_RECHAZADA', 'APERTURA_RECHAZADA', 'ESCRITURA_DENEGADA')
                AND motivo IS NULL AND movimiento_id IS NOT NULL));
