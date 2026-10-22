-- AYNI-327 · HU-05 escenario 3: un operador puede rechazar la solicitud, con su motivo
--
-- El estado RECHAZADA existía desde la V2, pero nada llevaba a él. El motivo se guarda en
-- la propia solicitud para poder responder después por qué se rechazó; es una lista
-- cerrada (MotivoDeRechazoDeSolicitud) y no un texto libre, porque es lo que lee el cliente.

ALTER TABLE solicitud_onboarding ADD COLUMN motivo_rechazo VARCHAR(32);

ALTER TABLE solicitud_onboarding ADD CONSTRAINT ck_solicitud_motivo_rechazo
    CHECK (motivo_rechazo IN ('SELFIE_NO_COINCIDE', 'DOCUMENTO_NO_VALIDO', 'DATOS_NO_COINCIDEN'));

-- Rechazada si y solo si tiene motivo: ni un rechazo sin explicación, ni un motivo suelto en
-- una solicitud que no se rechazó.
ALTER TABLE solicitud_onboarding ADD CONSTRAINT ck_solicitud_rechazo_coherente
    CHECK ((estado = 'RECHAZADA') = (motivo_rechazo IS NOT NULL));

COMMENT ON COLUMN solicitud_onboarding.motivo_rechazo IS
    'Solo en las solicitudes RECHAZADAS: el MotivoDeRechazoDeSolicitud que eligio el operador.';
