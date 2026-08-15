-- Ayni Bank — creación de schemas por bounded context.
-- Ningún servicio lee tablas de otro: la comunicación es por API o por eventos.

CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS notification;

COMMENT ON SCHEMA identity     IS 'Quién eres: usuarios, personas, roles, onboarding, auditoría.';
COMMENT ON SCHEMA core         IS 'Tu dinero: cuentas, libro mayor, tarjetas, transferencias, outbox.';
COMMENT ON SCHEMA notification IS 'Qué te avisamos: plantillas, notificaciones, intentos de envío.';
