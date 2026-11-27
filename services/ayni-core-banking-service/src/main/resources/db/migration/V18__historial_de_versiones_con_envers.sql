-- ═══════════════════════════════════════════════════════════════════════════
--  V18 · Historial de versiones de cuenta y tarjeta con Hibernate Envers
--        (HU-15, AYNI-279)
--
--  Cada vez que una cuenta o una tarjeta cambia, Envers escribe su version
--  completa en la tabla _aud y una revision con el momento y el actor. Con eso
--  se reconstruye el estado en cualquier fecha (escenario 2) y se sabe quien
--  hizo cada cambio. El PAN no se versiona: no cambia nunca.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE revision_auditoria (
    id      BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    momento TIMESTAMPTZ  NOT NULL,
    -- Usuario de la sesion que hizo el cambio; nulo en procesos programados y en la siembra.
    actor   UUID
);

-- revtype: 0 alta, 1 cambio, 2 baja (convencion de Envers).
CREATE TABLE cuenta_aud (
    id          UUID        NOT NULL,
    rev         BIGINT      NOT NULL REFERENCES revision_auditoria (id),
    revtype     SMALLINT,
    usuario_id  UUID,
    producto_id SMALLINT,
    numero      VARCHAR(14),
    cci         VARCHAR(20),
    moneda      CHAR(3),
    estado      VARCHAR(16),
    abierta_en  TIMESTAMPTZ,
    PRIMARY KEY (id, rev)
);

CREATE TABLE tarjeta_aud (
    id          UUID        NOT NULL,
    rev         BIGINT      NOT NULL REFERENCES revision_auditoria (id),
    revtype     SMALLINT,
    cuenta_id   UUID,
    usuario_id  UUID,
    ultimos4    CHAR(4),
    vence_en    DATE,
    estado      VARCHAR(16),
    emitida_en  TIMESTAMPTZ,
    PRIMARY KEY (id, rev)
);

CREATE INDEX ix_revision_auditoria_momento ON revision_auditoria (momento);

-- El historial arranca con el estado de hoy: sin esta revision inicial, la consulta por fecha de
-- una cuenta que no ha cambiado desde que existe Envers no encontraria ninguna version.
WITH inicial AS (
    INSERT INTO revision_auditoria (momento) VALUES (now()) RETURNING id
), cuentas AS (
    INSERT INTO cuenta_aud (id, rev, revtype, usuario_id, producto_id, numero, cci, moneda, estado, abierta_en)
    SELECT c.id, inicial.id, 0, c.usuario_id, c.producto_id, c.numero, c.cci, c.moneda, c.estado, c.abierta_en
    FROM cuenta c CROSS JOIN inicial
)
INSERT INTO tarjeta_aud (id, rev, revtype, cuenta_id, usuario_id, ultimos4, vence_en, estado, emitida_en)
SELECT t.id, inicial.id, 0, t.cuenta_id, t.usuario_id, t.ultimos4, t.vence_en, t.estado, t.emitida_en
FROM tarjeta t CROSS JOIN inicial;

-- El historial tampoco se reescribe: solo insercion, con la misma funcion que protege la pista
-- de auditoria (V14). Envers, con su estrategia por defecto, solo inserta.
REVOKE UPDATE, DELETE, TRUNCATE ON revision_auditoria, cuenta_aud, tarjeta_aud FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON revision_auditoria, cuenta_aud, tarjeta_aud FROM CURRENT_USER;

CREATE TRIGGER tr_revision_auditoria_sin_cambios BEFORE UPDATE OR DELETE ON revision_auditoria
    FOR EACH ROW EXECUTE FUNCTION evento_auditoria_inalterable();
CREATE TRIGGER tr_cuenta_aud_sin_cambios BEFORE UPDATE OR DELETE ON cuenta_aud
    FOR EACH ROW EXECUTE FUNCTION evento_auditoria_inalterable();
CREATE TRIGGER tr_tarjeta_aud_sin_cambios BEFORE UPDATE OR DELETE ON tarjeta_aud
    FOR EACH ROW EXECUTE FUNCTION evento_auditoria_inalterable();
CREATE TRIGGER tr_revision_auditoria_sin_vaciado BEFORE TRUNCATE ON revision_auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION evento_auditoria_inalterable();
CREATE TRIGGER tr_cuenta_aud_sin_vaciado BEFORE TRUNCATE ON cuenta_aud
    FOR EACH STATEMENT EXECUTE FUNCTION evento_auditoria_inalterable();
CREATE TRIGGER tr_tarjeta_aud_sin_vaciado BEFORE TRUNCATE ON tarjeta_aud
    FOR EACH STATEMENT EXECUTE FUNCTION evento_auditoria_inalterable();

COMMENT ON TABLE revision_auditoria IS 'Revisiones de Envers: momento y actor de cada cambio en cuenta o tarjeta (HU-15). Solo insercion.';
COMMENT ON TABLE cuenta_aud IS 'Cada version de cada cuenta (Envers). Solo insercion.';
COMMENT ON TABLE tarjeta_aud IS 'Cada version de cada tarjeta, sin el PAN (Envers). Solo insercion.';
