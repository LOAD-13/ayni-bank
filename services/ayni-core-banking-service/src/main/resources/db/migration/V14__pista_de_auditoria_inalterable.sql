-- ═══════════════════════════════════════════════════════════════════════════
--  V14 · Pista de auditoria inalterable (HU-15, AYNI-276 y AYNI-277)
--
--  Cada registro guarda actor (usuario_id), accion (tipo), recurso, IP,
--  user-agent, resultado y momento (ocurrido_en). Y la tabla pasa a ser de
--  solo insercion: ni el usuario de la aplicacion puede cambiar ni borrar
--  lo escrito.
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE evento_auditoria ADD COLUMN recurso   VARCHAR(120);
ALTER TABLE evento_auditoria ADD COLUMN agente    VARCHAR(256);
ALTER TABLE evento_auditoria ADD COLUMN resultado VARCHAR(12);

-- Los registros anteriores se completan una sola vez, antes de cerrar la tabla.
UPDATE evento_auditoria
   SET resultado = CASE WHEN tipo IN ('OPERACION_RECHAZADA', 'APERTURA_RECHAZADA', 'ACCESO_AJENO_DENEGADO')
                        THEN 'RECHAZADO' ELSE 'EXITO' END;

ALTER TABLE evento_auditoria ALTER COLUMN resultado SET NOT NULL;
ALTER TABLE evento_auditoria ADD CONSTRAINT ck_evento_auditoria_resultado
    CHECK (resultado IN ('EXITO', 'RECHAZADO'));

COMMENT ON COLUMN evento_auditoria.recurso IS 'Metodo y ruta de la peticion que origino el evento; nulo en procesos programados.';
COMMENT ON COLUMN evento_auditoria.agente IS 'User-Agent de la peticion, recortado a 256 caracteres.';
COMMENT ON COLUMN evento_auditoria.resultado IS 'EXITO o RECHAZADO.';

-- Solo insercion. Dos barreras: sin permiso de UPDATE, DELETE ni TRUNCATE para nadie, y un
-- trigger que rechaza esas operaciones con el codigo de "permiso denegado" (42501). El trigger
-- cubre lo que un REVOKE no alcanza: al dueno de la tabla y a un superusuario.
REVOKE UPDATE, DELETE, TRUNCATE ON evento_auditoria FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON evento_auditoria FROM CURRENT_USER;

CREATE FUNCTION evento_auditoria_inalterable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'La pista de auditoria es de solo insercion: % no esta permitido', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER tr_evento_auditoria_sin_cambios
    BEFORE UPDATE OR DELETE ON evento_auditoria
    FOR EACH ROW EXECUTE FUNCTION evento_auditoria_inalterable();

CREATE TRIGGER tr_evento_auditoria_sin_vaciado
    BEFORE TRUNCATE ON evento_auditoria
    FOR EACH STATEMENT EXECUTE FUNCTION evento_auditoria_inalterable();

COMMENT ON TABLE evento_auditoria IS
    'Pista de auditoria de solo insercion (HU-15): sin UPDATE, DELETE ni TRUNCATE. Sin importes ni numeros de cuenta.';
