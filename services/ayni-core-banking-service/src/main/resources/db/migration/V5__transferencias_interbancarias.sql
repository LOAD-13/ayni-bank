
-- AYNI-22 / HU-11
-- Registro de transferencias interbancarias

CREATE TABLE transferencia_interbancaria (
    id UUID PRIMARY KEY,
    clave UUID NOT NULL UNIQUE,
    usuario_id UUID NOT NULL,
    origen_id UUID NOT NULL REFERENCES cuenta(id),
    cci_destino VARCHAR(20) NOT NULL,
    importe NUMERIC(14,2) NOT NULL,
    concepto VARCHAR(140) NOT NULL
        DEFAULT 'Transferencia interbancaria',
    estado VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
    motivo VARCHAR(100),
    movimiento_cargo UUID NOT NULL UNIQUE,
    movimiento_reversion UUID UNIQUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),
    vence_en TIMESTAMPTZ NOT NULL,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_interbancaria_importe
        CHECK (importe BETWEEN 1 AND 5000),

    CONSTRAINT ck_interbancaria_cci
        CHECK (cci_destino ~ '^[0-9]{20}$'),

    CONSTRAINT ck_interbancaria_estado
        CHECK (estado IN (
            'PENDIENTE',
            'COMPLETADA',
            'RECHAZADA'
        )),

    CONSTRAINT ck_interbancaria_reversion
        CHECK (
            estado <> 'RECHAZADA'
            OR movimiento_reversion IS NOT NULL
        )
);

CREATE INDEX ix_interbancaria_pendiente
ON transferencia_interbancaria(vence_en)
WHERE estado = 'PENDIENTE';

-- Beneficiarios frecuentes

CREATE TABLE beneficiario_interbancario (
    id UUID PRIMARY KEY,
    usuario_id UUID NOT NULL,
    alias VARCHAR(80) NOT NULL,
    cci VARCHAR(20) NOT NULL,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_beneficiario_usuario_cci
        UNIQUE(usuario_id, cci),

    CONSTRAINT ck_beneficiario_cci
        CHECK (cci ~ '^[0-9]{20}$')
);
