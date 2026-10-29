-- ═══════════════════════════════════════════════════════════════════════════
--  notification · V3 · Aviso del interés capitalizado (HU-06 · AYNI-17)
--
--  El escenario 2 de HU-06 exige notificar al cliente el interés ganado en el
--  mes. core-banking publica core.interes.capitalizado al abonarlo.
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO plantilla (id, codigo, canal, asunto, cuerpo) VALUES
    (11, 'INTERES_CAPITALIZADO', 'EMAIL',
        'Tus intereses de {{periodo}}',
        'Abonamos {{moneda}} {{importe}} de intereses de {{periodo}} en tu cuenta {{cuenta}} el {{fecha}}. '
            || 'Tu saldo disponible es {{moneda}} {{saldo}}. Tu dinero rindió cada día, sin comisión de mantenimiento. '
            || 'Operación {{operacion}}.');
