-- ═══════════════════════════════════════════════════════════════════════════
--  V16 · Conversion inalterable y con su referencia (HU-12, AYNI-272)
--
--  Cada conversion guarda, ademas del tipo aplicado, de que referencia oficial
--  salio, el margen y el segmento que se uso y la fuente. Con eso una auditoria
--  recalcula el tipo y el importe recibido. Y la tabla pasa a ser de solo
--  insercion, igual que la pista de auditoria (V14).
-- ═══════════════════════════════════════════════════════════════════════════

ALTER TABLE conversion ADD COLUMN tipo_referencia NUMERIC(19,6);
ALTER TABLE conversion ADD COLUMN spread          NUMERIC(9,6);
ALTER TABLE conversion ADD COLUMN segmento        VARCHAR(24);
ALTER TABLE conversion ADD COLUMN fuente          VARCHAR(32);

-- Las conversiones anteriores se hicieron sin esta politica: se completan una sola vez, antes de
-- cerrar la tabla, con el tipo aplicado como referencia y sin margen.
UPDATE conversion
   SET tipo_referencia = tipo_aplicado,
       spread          = 0,
       segmento        = 'ESTANDAR',
       fuente          = 'HISTORICO';

ALTER TABLE conversion ALTER COLUMN tipo_referencia SET NOT NULL;
ALTER TABLE conversion ALTER COLUMN spread          SET NOT NULL;
ALTER TABLE conversion ALTER COLUMN segmento        SET NOT NULL;
ALTER TABLE conversion ALTER COLUMN fuente          SET NOT NULL;

ALTER TABLE conversion ADD CONSTRAINT ck_conversion_referencia CHECK (tipo_referencia > 0);
ALTER TABLE conversion ADD CONSTRAINT ck_conversion_spread CHECK (spread >= 0 AND spread < 1);
ALTER TABLE conversion ADD CONSTRAINT fk_conversion_segmento
    FOREIGN KEY (segmento) REFERENCES segmento_cambiario (codigo);

COMMENT ON COLUMN conversion.tipo_referencia IS 'Soles por dolar oficial del que salio el tipo aplicado: compra o venta segun el sentido.';
COMMENT ON COLUMN conversion.spread IS 'Margen total del segmento al momento de convertir, en tanto por uno.';
COMMENT ON COLUMN conversion.segmento IS 'Codigo del segmento cambiario aplicado.';
COMMENT ON COLUMN conversion.fuente IS 'Fuente de la referencia oficial (BCRP); HISTORICO en las anteriores a esta politica.';

-- Solo insercion: sin permiso de UPDATE, DELETE ni TRUNCATE para nadie, y un trigger que rechaza
-- esas operaciones con "permiso denegado" (42501) incluso al dueno de la tabla (ver V14).
REVOKE UPDATE, DELETE, TRUNCATE ON conversion FROM PUBLIC;
REVOKE UPDATE, DELETE, TRUNCATE ON conversion FROM CURRENT_USER;

CREATE FUNCTION conversion_inalterable() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'La conversion es de solo insercion: % no esta permitido', TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;

CREATE TRIGGER tr_conversion_sin_cambios
    BEFORE UPDATE OR DELETE ON conversion
    FOR EACH ROW EXECUTE FUNCTION conversion_inalterable();

CREATE TRIGGER tr_conversion_sin_vaciado
    BEFORE TRUNCATE ON conversion
    FOR EACH STATEMENT EXECUTE FUNCTION conversion_inalterable();

COMMENT ON TABLE conversion IS
    'Une las dos patas de una conversion y guarda el tipo aplicado con su referencia, margen y segmento: con estos datos se recalcula el importe recibido (HU-12, escenario 3). De solo insercion.';
