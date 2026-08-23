-- ═══════════════════════════════════════════════════════════════════════════
--  notification · V1 · Catálogo de plantillas
--
--  Alcance deliberado: solo datos de referencia. Las tablas notificacion e
--  intento_envio llegan con la Historia de Usuario que las necesita: una
--  migración fusionada no se edita nunca.
--
--  El canal SMS se incorporó al alcance en la revisión del 21 de agosto y se
--  atiende con un proveedor simulado de contrato definido (§12).
-- ═══════════════════════════════════════════════════════════════════════════

CREATE TABLE plantilla (
    id          SMALLINT     PRIMARY KEY,
    codigo      VARCHAR(48)  NOT NULL,
    canal       VARCHAR(16)  NOT NULL,
    idioma      CHAR(2)      NOT NULL DEFAULT 'es',
    asunto      VARCHAR(160),
    cuerpo      TEXT         NOT NULL,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_plantilla_canal  CHECK (canal IN ('EMAIL', 'SMS', 'PUSH')),
    CONSTRAINT ck_plantilla_asunto CHECK (canal <> 'EMAIL' OR asunto IS NOT NULL)
);

CREATE UNIQUE INDEX ux_plantilla_codigo_canal_idioma
    ON plantilla (codigo, canal, idioma);

COMMENT ON TABLE  plantilla        IS 'Plantillas de mensajes transaccionales.';
COMMENT ON COLUMN plantilla.cuerpo IS 'Marcadores entre llaves dobles. Nunca deben contener PAN, contraseña, token ni número de documento.';

-- ─── Datos de referencia ───────────────────────────────────────────────────
-- Sin plantillas el servicio no puede notificar nada, así que van en la
-- migración versionada y no en el seed de desarrollo.

INSERT INTO plantilla (id, codigo, canal, asunto, cuerpo) VALUES
    (1, 'BIENVENIDA', 'EMAIL',
        'Bienvenido a Ayni Bank',
        'Hola {{nombres}}, tu cuenta Ayni ya está activa. Tu dinero rinde desde hoy, con devengo diario y sin comisión de mantenimiento.'),

    (2, 'KYC_APROBADO', 'EMAIL',
        'Tu identidad fue verificada',
        'Hola {{nombres}}, verificamos tu identidad correctamente. Ya puedes operar con normalidad.'),

    (3, 'KYC_RECHAZADO', 'EMAIL',
        'Necesitamos revisar tus datos',
        'Hola {{nombres}}, no pudimos verificar tu identidad automáticamente. Un analista revisará tu solicitud en las próximas 24 horas.'),

    (4, 'CODIGO_VERIFICACION', 'SMS',
        NULL,
        'Ayni: tu código de verificación es {{codigo}}. Vence en {{minutos}} minutos. No lo compartas con nadie.'),

    (5, 'TRANSFERENCIA_ENVIADA', 'EMAIL',
        'Transferencia enviada',
        'Enviaste {{moneda}} {{importe}} a {{beneficiario}} el {{fecha}}. Operación {{codigo_operacion}}.'),

    (6, 'TRANSFERENCIA_RECIBIDA', 'EMAIL',
        'Recibiste una transferencia',
        'Recibiste {{moneda}} {{importe}} de {{remitente}} el {{fecha}}. Operación {{codigo_operacion}}.'),

    (7, 'TRANSFERENCIA_RECIBIDA', 'SMS',
        NULL,
        'Ayni: recibiste {{moneda}} {{importe}} de {{remitente}}. Saldo disponible {{moneda}} {{saldo}}.'),

    (8, 'TARJETA_CONGELADA', 'PUSH',
        NULL,
        'Congelaste tu tarjeta terminada en {{ultimos4}}. Puedes reactivarla cuando quieras desde la app.'),

    (9, 'ACCESO_NUEVO_DISPOSITIVO', 'EMAIL',
        'Nuevo inicio de sesión',
        'Detectamos un inicio de sesión desde {{dispositivo}} el {{fecha}}. Si no fuiste tú, cambia tu contraseña de inmediato.');
