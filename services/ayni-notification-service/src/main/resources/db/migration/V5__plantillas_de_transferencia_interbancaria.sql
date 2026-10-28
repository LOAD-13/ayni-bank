-- ═══════════════════════════════════════════════════════════════════════════
--  notification · V5 · Avisos de la transferencia interbancaria (HU-11 · AYNI-22)
--
--  Los escenarios 1 y 2 de HU-11 exigen avisar al cliente cuando la cámara acepta la
--  transferencia y cuando la rechaza. core-banking publica una sola vez el resultado
--  (core.transferencia.interbancaria.resuelta) y aquí se elige la plantilla.
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO plantilla (id, codigo, canal, asunto, cuerpo) VALUES
    (13, 'INTERBANCARIA_COMPLETADA', 'EMAIL',
        'Tu transferencia a otro banco llegó',
        'Enviaste {{moneda}} {{importe}} desde tu cuenta {{cuenta}} al {{contraparte}} el {{fecha}}. '
            || 'El otro banco ya la confirmó. Tu saldo disponible es {{moneda}} {{saldo}}. '
            || 'Operación {{operacion}}.'),
    (14, 'INTERBANCARIA_RECHAZADA', 'EMAIL',
        'Te devolvimos tu transferencia a otro banco',
        'No pudimos completar tu transferencia de {{moneda}} {{importe}} al {{contraparte}}: {{motivo}}. '
            || 'Devolvimos el dinero a tu cuenta {{cuenta}} el {{fecha}}; tu saldo disponible es {{moneda}} {{saldo}}. '
            || 'Revisa el CCI con quien te lo dio e inténtalo de nuevo. Operación {{operacion}}.');
