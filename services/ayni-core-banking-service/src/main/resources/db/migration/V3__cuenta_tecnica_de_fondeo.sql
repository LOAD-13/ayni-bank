-- HU-07 / HU-08 · Cuenta técnica de fondeo para los depósitos simulados
--
-- Partida doble estricta: todo movimiento tiene un cargo y un abono que suman
-- cero. Un depósito que viene «de fuera» del banco necesita también su
-- contrapartida, y esa es esta cuenta: cada depósito simulado es un CARGO aquí y
-- un ABONO en la cuenta del cliente. Su saldo, siempre negativo, es exactamente
-- el total de dinero que ha entrado al sistema por esta vía, y se puede auditar
-- en cualquier momento.
--
-- No pertenece a ningún cliente: su titular es el identificador reservado
-- 00000000-0000-0000-0000-000000000001, que identity nunca asigna (genera UUID
-- aleatorios v4). El número 0011-0000000001 está por debajo del inicio de la
-- secuencia de clientes (1000000001), así que no puede colisionar con ninguno.
-- El adaptador la excluye de la búsqueda por número: nadie puede transferirle.
INSERT INTO cuenta (id, usuario_id, producto_id, numero, cci, moneda, estado, abierta_en)
VALUES ('f0de0000-0000-4000-8000-000000000001',
        '00000000-0000-0000-0000-000000000001',
        1,
        '00110000000001',
        '99900101000000000103',
        'PEN',
        'ACTIVA',
        now());

COMMENT ON TABLE cuenta IS
    'Identidad de la cuenta. El saldo NO vive aqui: es la suma de sus asientos. '
    'La fila f0de0000-...-0001 es la cuenta tecnica de fondeo (V3).';
