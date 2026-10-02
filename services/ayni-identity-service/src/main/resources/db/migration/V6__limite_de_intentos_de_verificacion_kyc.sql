-- HU-02 · Limite de tres intentos y derivacion a revision manual (AYNI-13 subtarea 11)
--
-- Cuenta cuantas veces fallo la verificacion de una solicitud (documento
-- rechazado, dato declarado que no coincide con el OCR, o cualquier otro
-- motivo de negocio) para decidir cuando dejar de insistir. Es un contador
-- de intentos DEL USUARIO sobre el proceso completo de onboarding, distinto
-- del reintento HTTP de Resilience4j (subtarea 10, ADR-0020): ese reintenta
-- automaticamente una llamada de red que fallo; este decide cuantas veces
-- se deja a una PERSONA volver a intentar el proceso antes de derivarla a
-- revision manual (ADR-0021).
--
-- No cuenta la caida del propio kyc-service (`KycServiceNoDisponibleException`):
-- esa deriva a revision manual de inmediato, sin gastar intentos, porque
-- reintentar no depende de nada que el usuario pueda corregir. Ver ADR-0021.

ALTER TABLE solicitud_onboarding
    ADD COLUMN intentos_verificacion_kyc SMALLINT NOT NULL DEFAULT 0;

ALTER TABLE solicitud_onboarding
    ADD CONSTRAINT ck_solicitud_intentos_kyc
        CHECK (intentos_verificacion_kyc BETWEEN 0 AND 3);

COMMENT ON COLUMN solicitud_onboarding.intentos_verificacion_kyc IS
    'Intentos fallidos de verificacion que ha consumido el usuario. Al '
    'llegar a 3, la solicitud pasa a EN_REVISION_MANUAL. Ver ADR-0021.';
