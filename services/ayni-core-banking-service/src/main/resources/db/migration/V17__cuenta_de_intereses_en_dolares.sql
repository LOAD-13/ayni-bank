-- ═══════════════════════════════════════════════════════════════════════════
--  V17 · Cuenta tecnica de intereses en dolares (HU-12, AYNI-273)
--
--  El producto AHORRO_USD tiene tasa vigente (V1), asi que una cuenta en dolares
--  devenga y al cerrar el mes capitaliza. Sin una contrapartida en dolares, la
--  capitalizacion fallaria y, como el cierre de cada dia es una sola transaccion,
--  frenaria el cierre de todos los clientes. Igual que la de soles (V5): su saldo
--  negativo es el total de intereses en dolares que el banco ha pagado.
-- ═══════════════════════════════════════════════════════════════════════════

-- Mismo titular reservado que la de soles (…0003): ux_cuenta_titular_moneda admite una cuenta
-- activa por titular y moneda. El CCI se calcula con la comprobacion de control de Cci.
INSERT INTO cuenta (id, usuario_id, producto_id, numero, cci, moneda, estado, abierta_en, tecnica)
VALUES ('1e7e0000-0000-4000-8000-000000000002',
        '00000000-0000-0000-0000-000000000003',
        2,
        '00120000000003',
        '99900102000000000318',
        'USD',
        'ACTIVA',
        now(),
        TRUE);

COMMENT ON TABLE cuenta IS
    'Identidad de la cuenta. El saldo NO vive aqui: es la suma de sus asientos. '
    'Cuentas tecnicas: f0de0000-... fondeo (V3), cce00000-... compensacion (V4), 1e7e0000-...01 intereses en soles (V5), ca4b0000-... posicion cambiaria (V13) e 1e7e0000-...02 intereses en dolares (V17).';
