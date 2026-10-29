-- ═══════════════════════════════════════════════════════════════════════════
--  notification · V4 · Aviso del rechazo de la solicitud (HU-05 · AYNI-327)
--
--  El escenario 3 de HU-05 exige notificar al solicitante el motivo del rechazo.
--  identity publica identity.solicitud.rechazada con el motivo ya redactado.
--
--  No se reutiliza KYC_RECHAZADO (V1): su texto avisa de una revisión manual en
--  24 horas, que es lo contrario de un rechazo. El saludo con el nombre lo pone
--  el correo HTML, como en las demás plantillas.
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO plantilla (id, codigo, canal, asunto, cuerpo) VALUES
    (12, 'SOLICITUD_RECHAZADA', 'EMAIL',
        'No pudimos aprobar tu solicitud',
        'Revisamos la solicitud para abrir tu Cuenta Ayni y no pudimos aprobarla. Motivo: {{motivo}} '
            || 'No se abrió ninguna cuenta a tu nombre y tus datos siguen protegidos.');
