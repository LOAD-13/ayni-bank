-- HU-22 · Segundo factor a eleccion y verificacion del contacto en el registro (AYNI-135)
--
-- Con la incorporacion del paso 2 (eleccion y verificacion del segundo factor) entre los
-- datos iniciales y la captura del DNI, el proceso de onboarding pasa de cinco a seis pasos.

ALTER TABLE solicitud_onboarding DROP CONSTRAINT ck_solicitud_paso;
ALTER TABLE solicitud_onboarding ADD CONSTRAINT ck_solicitud_paso
    CHECK (paso_actual BETWEEN 1 AND 6);
