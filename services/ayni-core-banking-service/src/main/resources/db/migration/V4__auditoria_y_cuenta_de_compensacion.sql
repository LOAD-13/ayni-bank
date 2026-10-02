-- AYNI-158 · Pista de auditoría de operaciones y cuenta de compensación interbancaria
--
--   evento_auditoria   toda operación monetaria, aceptada o rechazada
--   cuenta (fila)      contrapartida de las transferencias con otros bancos

-- ─── evento_auditoria ──────────────────────────────────────────────────────
-- Igual que en identity, es una tabla y no un log: un log rota, se trunca y no
-- se puede consultar por cliente. Aquí se responde «qué intentó hacer este
-- cliente y qué pasó», incluidos los rechazos, que no dejan asientos.
--
-- No guarda importes ni números de cuenta: el importe ya está en los asientos
-- del movimiento, al que se llega por movimiento_id.
CREATE TABLE evento_auditoria (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo            VARCHAR(32)  NOT NULL,
    usuario_id      UUID         NOT NULL,
    -- Nulo en los rechazos: no llegó a existir movimiento.
    movimiento_id   UUID,
    -- Solo en los rechazos: el MotivoDeRechazo del dominio.
    motivo          VARCHAR(40),
    ocurrido_en     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_evento_auditoria_tipo
        CHECK (tipo IN ('TRANSFERENCIA_REALIZADA', 'DEPOSITO_SIMULADO',
                        'OPERACION_REPETIDA', 'OPERACION_RECHAZADA')),
    -- Un rechazo siempre dice por qué y nunca tiene movimiento; el resto, al revés.
    CONSTRAINT ck_evento_auditoria_coherente
        CHECK ((tipo = 'OPERACION_RECHAZADA' AND motivo IS NOT NULL AND movimiento_id IS NULL)
            OR (tipo <> 'OPERACION_RECHAZADA' AND motivo IS NULL AND movimiento_id IS NOT NULL))
);

CREATE INDEX ix_evento_auditoria_usuario ON evento_auditoria (usuario_id, ocurrido_en DESC);
CREATE INDEX ix_evento_auditoria_tipo ON evento_auditoria (tipo, ocurrido_en DESC);

COMMENT ON TABLE evento_auditoria IS
    'Pista de auditoria de operaciones monetarias, incluidos los rechazos. Sin importes ni numeros de cuenta.';

-- ─── cuenta de compensación interbancaria ─────────────────────────────────
-- Una transferencia que llega de otro banco también son DOS asientos, no uno:
-- el ABONO en la cuenta del cliente y el CARGO en esta cuenta, que representa
-- lo que la Cámara de Compensación Electrónica (CCE) nos debe liquidar. Una
-- transferencia hacia otro banco es lo inverso. Su saldo es, en cada momento,
-- la posición neta frente a la CCE, y debe volver a cero tras cada liquidación.
-- Ver ADR-0029.
--
-- Titular reservado propio (…0002): ux_cuenta_titular_moneda admite una sola
-- cuenta activa por titular y moneda, y …0001 ya es el de la cuenta de fondeo.
-- Como esta, el adaptador la excluye de la búsqueda por número.
INSERT INTO cuenta (id, usuario_id, producto_id, numero, cci, moneda, estado, abierta_en)
VALUES ('cce00000-0000-4000-8000-000000000001',
        '00000000-0000-0000-0000-000000000002',
        1,
        '00110000000002',
        '99900101000000000206',
        'PEN',
        'ACTIVA',
        now());

COMMENT ON TABLE cuenta IS
    'Identidad de la cuenta. El saldo NO vive aqui: es la suma de sus asientos. '
    'Cuentas tecnicas: f0de0000-... fondeo (V3) y cce00000-... compensacion interbancaria (V4).';
