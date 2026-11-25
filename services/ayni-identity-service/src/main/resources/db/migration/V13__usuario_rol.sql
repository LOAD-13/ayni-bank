-- ═══════════════════════════════════════════════════════════════════════════
--  identity · V13 · Qué rol tiene cada usuario (HU-17 · AYNI-281)
--
--  El catálogo de roles y permisos existe desde V1, pero nada decía quién tiene qué.
--  Esta tabla lo dice, y de ella sale el claim `permisos` del token de acceso: el
--  gateway decide con él qué rutas puede usar cada quien.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE usuario_rol (
    usuario_id  UUID        NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    rol_id      SMALLINT    NOT NULL REFERENCES rol (id),
    asignado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, rol_id)
);

CREATE INDEX idx_usuario_rol_rol ON usuario_rol (rol_id);

COMMENT ON TABLE usuario_rol IS
    'Roles de cada usuario (§5.4 del diseño). Todo usuario nace CLIENTE; el personal recibe además su rol.';

-- Quienes ya se habían registrado son clientes.
INSERT INTO usuario_rol (usuario_id, rol_id)
SELECT id, (SELECT id FROM rol WHERE codigo = 'CLIENTE') FROM usuario;

-- Todo usuario nuevo nace CLIENTE, sea cual sea el camino por el que se cree. Va en la base y no
-- en el código de registro para que ninguna ruta de alta pueda dejar a alguien sin rol, que con el
-- gateway decidiendo por permisos sería alguien que no puede usar nada.
CREATE FUNCTION asignar_rol_de_cliente() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    -- Con el schema escrito: el trigger corre con el search_path de quien inserta, no con el de Flyway.
    INSERT INTO identity.usuario_rol (usuario_id, rol_id)
    SELECT NEW.id, id FROM identity.rol WHERE codigo = 'CLIENTE';
    RETURN NEW;
END;
$$;

CREATE TRIGGER tr_usuario_rol_inicial
    AFTER INSERT ON usuario
    FOR EACH ROW EXECUTE FUNCTION asignar_rol_de_cliente();
