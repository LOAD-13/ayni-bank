-- ═══════════════════════════════════════════════════════════════════════════
--  identity · V14 · Historial de versiones de los datos de la persona con
--  Hibernate Envers (HU-15 · AYNI-279)
--
--  La persona se mantiene en `usuario` (correo, celular, estado, consentimiento):
--  la tabla `persona` de V2 no la escribe ningun flujo, asi que versionarla no
--  dejaria rastro de nada. El hash de la contrasena no entra en el historial.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE revision_auditoria (
    id      BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    momento TIMESTAMPTZ  NOT NULL,
    -- Usuario de la sesion que hizo el cambio; nulo en el registro, en procesos programados y en la siembra.
    actor   UUID
);

-- revtype: 0 alta, 1 cambio, 2 baja (convencion de Envers).
CREATE TABLE usuario_aud (
    id                UUID         NOT NULL,
    rev               BIGINT       NOT NULL REFERENCES revision_auditoria (id),
    revtype           SMALLINT,
    correo            VARCHAR(254),
    celular           VARCHAR(9),
    estado            VARCHAR(24),
    consentimiento_en TIMESTAMPTZ,
    terminos_version  VARCHAR(16),
    registrado_en     TIMESTAMPTZ,
    actualizado_en    TIMESTAMPTZ,
    PRIMARY KEY (id, rev)
);

CREATE INDEX ix_revision_auditoria_momento ON revision_auditoria (momento);

-- El historial arranca con los datos de hoy, para poder consultar el estado desde este momento.
WITH inicial AS (
    INSERT INTO revision_auditoria (momento) VALUES (now()) RETURNING id
)
INSERT INTO usuario_aud (id, rev, revtype, correo, celular, estado, consentimiento_en, terminos_version,
                         registrado_en, actualizado_en)
SELECT u.id, inicial.id, 0, u.correo, u.celular, u.estado, u.consentimiento_en, u.terminos_version,
       u.registrado_en, u.actualizado_en
FROM usuario u CROSS JOIN inicial;

-- Solo insercion: ni el usuario de la aplicacion reescribe el historial. El trigger cubre tambien
-- al dueno de la tabla y a un superusuario, a los que un REVOKE no alcanza.
REVOKE UPDATE, DELETE, TRUNCATE ON revision_auditoria, usuario_aud FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON revision_auditoria, usuario_aud FROM CURRENT_USER;

CREATE FUNCTION historial_inalterable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'El historial es de solo insercion: % no esta permitido', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER tr_revision_auditoria_sin_cambios BEFORE UPDATE OR DELETE ON revision_auditoria
    FOR EACH ROW EXECUTE FUNCTION historial_inalterable();
CREATE TRIGGER tr_usuario_aud_sin_cambios BEFORE UPDATE OR DELETE ON usuario_aud
    FOR EACH ROW EXECUTE FUNCTION historial_inalterable();
CREATE TRIGGER tr_revision_auditoria_sin_vaciado BEFORE TRUNCATE ON revision_auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION historial_inalterable();
CREATE TRIGGER tr_usuario_aud_sin_vaciado BEFORE TRUNCATE ON usuario_aud
    FOR EACH STATEMENT EXECUTE FUNCTION historial_inalterable();

COMMENT ON TABLE revision_auditoria IS 'Revisiones de Envers: momento y actor de cada cambio en usuario (HU-15). Solo insercion.';
COMMENT ON TABLE usuario_aud IS 'Cada version de los datos de la persona, sin la contrasena (Envers). Solo insercion.';
