-- ═══════════════════════════════════════════════════════════════════════════
--  identity · V1 · Catálogo de roles y permisos
--
--  Alcance deliberado: solo datos de referencia. Las tablas transaccionales
--  (usuario, persona, solicitud_onboarding, refresh_token…) llegan con la
--  Historia de Usuario que las necesita, no antes: una migración fusionada no
--  se edita nunca, así que escribir DDL a cuenta de historias sin refinar
--  obliga a arrastrar el error con una migración correctiva.
--
--  Los roles reproducen la segregación de funciones del §5.4 del documento de
--  diseño. No existe un rol de superadministrador, y es intencional: en banca
--  nadie completa por sí solo una operación sensible.
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE rol (
    id          SMALLINT     PRIMARY KEY,
    codigo      VARCHAR(32)  NOT NULL UNIQUE,
    nombre      VARCHAR(64)  NOT NULL,
    descripcion VARCHAR(255) NOT NULL,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  rol        IS 'Roles del sistema. Ver §5.4 del documento de diseño.';
COMMENT ON COLUMN rol.codigo IS 'Identificador estable usado por el código. No se renombra.';

CREATE TABLE permiso (
    id          SMALLINT     PRIMARY KEY,
    codigo      VARCHAR(64)  NOT NULL UNIQUE,
    descripcion VARCHAR(255) NOT NULL
);

COMMENT ON COLUMN permiso.codigo IS 'Formato recurso:accion, por ejemplo cuenta:leer.';

CREATE TABLE rol_permiso (
    rol_id     SMALLINT NOT NULL REFERENCES rol(id)     ON DELETE CASCADE,
    permiso_id SMALLINT NOT NULL REFERENCES permiso(id) ON DELETE CASCADE,
    PRIMARY KEY (rol_id, permiso_id)
);

CREATE INDEX idx_rol_permiso_permiso ON rol_permiso (permiso_id);

-- ─── Datos de referencia ───────────────────────────────────────────────────
-- Van en la migración versionada, no en el seed de desarrollo: sin roles el
-- sistema no arranca en ningún entorno, producción incluida.

INSERT INTO rol (id, codigo, nombre, descripcion) VALUES
    (1, 'CLIENTE',              'Cliente',                'Opera únicamente sus propias cuentas.'),
    (2, 'OPERADOR',             'Operador',               'Consulta clientes e inicia bloqueos. No aprueba por sí solo ni ve el PAN completo.'),
    (3, 'SUPERVISOR',           'Supervisor',             'Aprueba lo iniciado por el operador. No puede iniciar y aprobar la misma operación.'),
    (4, 'AUDITOR',              'Auditor',                'Lectura total, incluida la pista de auditoría. No escribe nada.'),
    (5, 'OFICIAL_CUMPLIMIENTO', 'Oficial de Cumplimiento','Revisa KYC y marca operaciones sospechosas. No mueve dinero.');

INSERT INTO permiso (id, codigo, descripcion) VALUES
    (1,  'cuenta:leer',              'Consultar cuentas propias'),
    (2,  'cuenta:leer_todas',        'Consultar cuentas de cualquier cliente'),
    (3,  'transferencia:iniciar',    'Iniciar una transferencia'),
    (4,  'transferencia:aprobar',    'Aprobar una transferencia iniciada por otro'),
    (5,  'tarjeta:administrar',      'Congelar, descongelar y ajustar límites de la tarjeta propia'),
    (6,  'tarjeta:revelar_pan',      'Revelar el PAN completo, con MFA'),
    (7,  'cliente:leer',             'Consultar la ficha de un cliente'),
    (8,  'cliente:bloquear_iniciar', 'Iniciar el bloqueo de un cliente'),
    (9,  'cliente:bloquear_aprobar', 'Aprobar el bloqueo de un cliente'),
    (10, 'kyc:revisar',              'Revisar manualmente una solicitud de onboarding'),
    (11, 'kyc:marcar_sospechoso',    'Marcar una operación como sospechosa'),
    (12, 'auditoria:leer',           'Leer la pista de auditoría completa');

INSERT INTO rol_permiso (rol_id, permiso_id) VALUES
    -- CLIENTE
    (1, 1), (1, 3), (1, 5), (1, 6),
    -- OPERADOR: inicia, no aprueba
    (2, 2), (2, 7), (2, 8),
    -- SUPERVISOR: aprueba lo que otro inició
    (3, 2), (3, 7), (3, 4), (3, 9),
    -- AUDITOR: solo lectura
    (4, 2), (4, 7), (4, 12),
    -- OFICIAL_CUMPLIMIENTO: revisa, no mueve dinero
    (5, 7), (5, 10), (5, 11);
