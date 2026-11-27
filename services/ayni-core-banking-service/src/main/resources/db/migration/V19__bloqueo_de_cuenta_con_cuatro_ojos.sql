-- ═══════════════════════════════════════════════════════════════════════════
--  V19 · Bloqueo de cuenta con el principio de cuatro ojos (HU-15, AYNI-282)
--
--  Un operador pide el bloqueo y otra persona, un supervisor, lo aprueba.
--  La base tambien lo hace cumplir: una solicitud no puede tener el mismo
--  aprobador que iniciador, y una cuenta no puede tener dos pendientes.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE solicitud_bloqueo (
    id            UUID         PRIMARY KEY,
    cuenta_id     UUID         NOT NULL REFERENCES cuenta (id),
    motivo        VARCHAR(200) NOT NULL,
    iniciada_por  UUID         NOT NULL,
    iniciada_en   TIMESTAMPTZ  NOT NULL,
    aprobada_por  UUID,
    aprobada_en   TIMESTAMPTZ,

    CONSTRAINT ck_solicitud_bloqueo_cuatro_ojos
        CHECK (aprobada_por IS NULL OR aprobada_por <> iniciada_por),
    CONSTRAINT ck_solicitud_bloqueo_aprobacion
        CHECK ((aprobada_por IS NULL) = (aprobada_en IS NULL)),
    CONSTRAINT ck_solicitud_bloqueo_motivo
        CHECK (length(trim(motivo)) > 0)
);

CREATE UNIQUE INDEX ux_solicitud_bloqueo_pendiente
    ON solicitud_bloqueo (cuenta_id) WHERE aprobada_por IS NULL;

COMMENT ON TABLE solicitud_bloqueo IS
    'Bloqueos de cuenta pedidos por un operador y aprobados por otra persona (HU-15, cuatro ojos).';

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
                    'BLOQUEO_SOLICITADO', 'CUENTA_BLOQUEADA', 'CUATRO_OJOS_DENEGADO'));
