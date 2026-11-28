-- Hotfix 2.2.1. V18 le quito UPDATE al duenio de revision_auditoria. La llave foranea de cuenta_aud y tarjeta_aud
-- comprueba la revision con SELECT ... FOR KEY SHARE, y PostgreSQL exige para eso UPDATE (o DELETE o
-- TRUNCATE) sobre la tabla referenciada. Con un usuario sin superusuario, como ayni_app en RDS, cada
-- version nueva fallaba con "permission denied". Las pruebas no lo vieron porque corrian como superusuario.
--
-- Se devuelve solo UPDATE y solo al duenio. El historial sigue sin poder reescribirse: el trigger
-- tr_revision_auditoria_sin_cambios rechaza cualquier UPDATE o DELETE con 42501.
GRANT UPDATE ON revision_auditoria TO CURRENT_USER;
