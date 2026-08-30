-- HU-01 · Datos de identidad declarados en el paso 1 del onboarding
--
-- El formulario aprobado pide nombres, apellidos, documento y fecha de nacimiento antes
-- de la verificacion. Hasta ahora el servidor los descartaba. Se guardan aqui, en la
-- solicitud, y no en `persona`, por una razon que no es de comodidad:
--
--   solicitud_onboarding.*_declarado   lo que la persona escribio. No prueba nada.
--   persona.*                          lo que se comprobo contra el documento fisico.
--
-- HU-02 lee el DNI con OCR y contrasta lo extraido con lo declarado. Si coinciden, se
-- escribe `persona` y la identidad queda verificada. Si no, la solicitud va a revision
-- manual. Guardar las dos lecturas en la misma fila haria imposible esa comparacion y,
-- sobre todo, imposible saber despues de donde salio cada dato.
--
-- V2 no se toca: una migracion fusionada no se edita nunca.

ALTER TABLE solicitud_onboarding
    ADD COLUMN nombres_declarados           VARCHAR(80),
    ADD COLUMN apellidos_declarados         VARCHAR(120),
    ADD COLUMN tipo_documento_declarado     VARCHAR(16),
    -- Criptograma AES-256-GCM, igual que persona.numero_documento. El IV es aleatorio en
    -- cada cifrado, asi que esta columna NO sirve para buscar ni para imponer unicidad:
    -- el mismo documento produce criptogramas distintos. La deteccion de duplicados se
    -- hace en HU-02 sobre la identidad ya verificada.
    ADD COLUMN documento_declarado          VARCHAR(255),
    ADD COLUMN documento_declarado_ultimos4 VARCHAR(4),
    ADD COLUMN fecha_nacimiento_declarada   DATE;

-- Nulables, y no por descuido: las solicitudes senuelo del escenario 2 no llevan ninguno
-- de estos datos. No se crea usuario, luego no hay nada que declarar, y guardar los datos
-- personales de un intento sobre una cuenta ajena contradiria el principio de
-- minimizacion de la Ley N.o 29733. Que el senuelo tenga estas columnas vacias no lo
-- delata: la tabla no se expone por ninguna API. Ver ADR-0008.
ALTER TABLE solicitud_onboarding
    ADD CONSTRAINT ck_solicitud_tipo_documento_declarado
        CHECK (tipo_documento_declarado IS NULL
               OR tipo_documento_declarado IN ('DNI', 'CE', 'PASAPORTE'));

COMMENT ON COLUMN solicitud_onboarding.documento_declarado IS
    'Criptograma AES-256-GCM del numero declarado. Nunca se registra en logs ni se '
    'devuelve por la API. Se descifra solo para contrastarlo con el OCR en HU-02.';
COMMENT ON COLUMN solicitud_onboarding.nombres_declarados IS
    'Lo que escribio la persona, no lo que se verifico. La identidad comprobada vive '
    'en persona.';
