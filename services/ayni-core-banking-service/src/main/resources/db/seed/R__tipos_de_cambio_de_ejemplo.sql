-- ═══════════════════════════════════════════════════════════════════════════
--  core · seed de desarrollo · Histórico de tipo de cambio
--
--  Solo se aplica con el perfil `dev`: application.yml añade classpath:db/seed
--  a las localizaciones de Flyway únicamente en ese perfil. En staging y en
--  producción el tipo de cambio lo puebla el trabajo diario que consulta a
--  SUNAT/BCRP.
--
--  Es una migración repetible (prefijo R): se vuelve a ejecutar cuando cambia
--  su contenido, y por eso debe ser idempotente. El ON CONFLICT se apoya en el
--  índice único ux_tipo_cambio_dia.
--
--  Valores realistas del entorno peruano, no exactos: sirven para operar y
--  probar la conversión sin depender de que la API externa esté disponible.
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO tipo_cambio (fecha, moneda_origen, moneda_destino, referencia_compra, referencia_venta, fuente)
SELECT
    dia::date,
    'USD',
    'PEN',
    valor,
    valor + 0.008000,
    'SEED_DEV'
FROM (
    VALUES
        (CURRENT_DATE - 6, 3.742000),
        (CURRENT_DATE - 5, 3.751000),
        (CURRENT_DATE - 4, 3.738000),
        (CURRENT_DATE - 3, 3.729000),
        (CURRENT_DATE - 2, 3.735000),
        (CURRENT_DATE - 1, 3.744000),
        (CURRENT_DATE,     3.748000)
) AS referencias(dia, valor)
ON CONFLICT (fecha, moneda_origen, moneda_destino, fuente) DO NOTHING;
