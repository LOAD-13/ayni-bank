-- AYNI-155 · Particionar core.asiento por mes e indices para la purga
--
-- Hallazgo del Taller de Diseno Fisico (semana 6). Una fila de asiento ocupa 289,5 bytes
-- con sus indices, y cada movimiento escribe dos por la partida doble. Con un millon de
-- usuarios eso son 4,37 M filas al dia y el disco de 200 GB se llena en 135 dias. Ningun
-- indice arregla un problema de volumen: hay que poder sacar los meses viejos de la tabla
-- caliente sin borrarlos, y eso exige particiones. Ver ADR-0036.
--
-- Nada referencia a asiento con clave foranea, asi que se puede reconstruir: se crea la
-- tabla particionada, se copian las filas y se borra la vieja, todo en la transaccion de
-- la migracion. Si algo falla, no queda nada a medias.

ALTER TABLE asiento RENAME TO asiento_sin_particionar;

-- La clave primaria de una tabla particionada tiene que incluir la columna de
-- particion: PostgreSQL solo sabe garantizar la unicidad dentro de cada particion.
-- El id sigue siendo un UUID que genera la aplicacion, unico en la practica.
CREATE TABLE asiento (
    id                  UUID          NOT NULL,
    cuenta_id           UUID          NOT NULL,
    movimiento_id       UUID          NOT NULL,
    tipo                VARCHAR(16)   NOT NULL,
    importe             NUMERIC(14,2) NOT NULL,
    moneda              CHAR(3)       NOT NULL,
    concepto            VARCHAR(140)  NOT NULL,
    registrado_en       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT pk_asiento
        PRIMARY KEY (id, registrado_en),
    CONSTRAINT fk_asiento_cuenta
        FOREIGN KEY (cuenta_id) REFERENCES cuenta (id),
    CONSTRAINT ck_asiento_tipo
        CHECK (tipo IN ('CARGO', 'ABONO')),
    CONSTRAINT ck_asiento_moneda
        CHECK (moneda IN ('PEN', 'USD')),
    CONSTRAINT ck_asiento_importe_positivo
        CHECK (importe > 0)
) PARTITION BY RANGE (registrado_en);

COMMENT ON TABLE asiento IS
    'Libro mayor por partida doble, una particion por mes en hora de Lima. ADR-0036.';
COMMENT ON COLUMN asiento.importe IS
    'NUMERIC exacto. Los tipos de coma flotante estan prohibidos en importes.';

-- ─── Creacion de particiones ───────────────────────────────────────────────
-- Los limites son el primer instante del mes en hora de Lima, no en UTC: el extracto
-- de octubre es el octubre del cliente. Con UTC, un asiento de las 23:30 del dia 31
-- caeria en el mes siguiente.
-- `SET search_path FROM CURRENT` fija el schema de la migracion (core): la funcion la
-- llama tambien la aplicacion, cuya conexion no tiene por que tener core en su ruta.
CREATE FUNCTION crear_particion_de_asiento(mes DATE) RETURNS BOOLEAN
    LANGUAGE plpgsql
    SET search_path FROM CURRENT
AS $$
DECLARE
    inicio DATE := date_trunc('month', mes)::date;
    fin    DATE := (date_trunc('month', mes) + interval '1 month')::date;
    nombre TEXT := 'asiento_' || to_char(inicio, 'YYYY_MM');
BEGIN
    IF to_regclass(nombre) IS NOT NULL THEN
        RETURN FALSE;
    END IF;
    EXECUTE format('CREATE TABLE %I PARTITION OF asiento FOR VALUES FROM (%L) TO (%L)',
                   nombre,
                   make_timestamptz(extract(year FROM inicio)::int, extract(month FROM inicio)::int, 1, 0, 0, 0, 'America/Lima'),
                   make_timestamptz(extract(year FROM fin)::int, extract(month FROM fin)::int, 1, 0, 0, 0, 'America/Lima'));
    RETURN TRUE;
END;
$$;

-- Del mes en curso a `meses_adelante` meses por delante. La llama a diario el
-- mantenimiento de core-banking; devuelve cuantas particiones creo.
CREATE FUNCTION crear_particiones_de_asiento(meses_adelante INT) RETURNS INT
    LANGUAGE plpgsql
    SET search_path FROM CURRENT
AS $$
DECLARE
    este_mes DATE := date_trunc('month', now() AT TIME ZONE 'America/Lima')::date;
    creadas  INT  := 0;
BEGIN
    FOR i IN 0..meses_adelante LOOP
        IF crear_particion_de_asiento((este_mes + make_interval(months => i))::date) THEN
            creadas := creadas + 1;
        END IF;
    END LOOP;
    RETURN creadas;
END;
$$;

-- Una particion por cada mes que ya tiene asientos, y tres meses por delante.
DO $$
DECLARE
    mes DATE;
BEGIN
    FOR mes IN
        SELECT generate_series(
                   date_trunc('month', COALESCE(min(registrado_en), now()) AT TIME ZONE 'America/Lima'),
                   date_trunc('month', now() AT TIME ZONE 'America/Lima'),
                   interval '1 month')::date
        FROM asiento_sin_particionar
    LOOP
        PERFORM crear_particion_de_asiento(mes);
    END LOOP;
END;
$$;

SELECT crear_particiones_de_asiento(3);

-- Red de seguridad: si el mantenimiento dejara de crear particiones, un asiento sin mes
-- propio caeria aqui en lugar de rechazarse y tumbar la transferencia. El mantenimiento
-- avisa si encuentra filas en ella.
CREATE TABLE asiento_default PARTITION OF asiento DEFAULT;

INSERT INTO asiento (id, cuenta_id, movimiento_id, tipo, importe, moneda, concepto, registrado_en)
SELECT id, cuenta_id, movimiento_id, tipo, importe, moneda, concepto, registrado_en
FROM asiento_sin_particionar;

DROP TABLE asiento_sin_particionar;

-- Los mismos indices que tenia, ahora uno por particion. Se crean despues de copiar las
-- filas: construir un indice de una vez es mas rapido que mantenerlo fila a fila.
-- ix_asiento_cuenta sirve tambien al devengo diario (cuenta_id y registrado_en < dia).
CREATE INDEX ix_asiento_cuenta ON asiento (cuenta_id, registrado_en DESC);
CREATE INDEX ix_asiento_movimiento ON asiento (movimiento_id);

-- ─── Indices que pidio el taller ───────────────────────────────────────────
-- Buscar las cuentas de un titular, incluidas las cerradas: ux_cuenta_titular_moneda
-- solo cubre las activas.
CREATE INDEX ix_cuenta_usuario ON cuenta (usuario_id);

-- La purga nocturna de claves con mas de 30 dias recorreria la tabla entera sin el.
CREATE INDEX ix_operacion_idempotente_registrado ON operacion_idempotente (registrado_en);
