-- HU-02 · Documentos KYC subidos a MinIO (AYNI-13 subtarea 9)
--
-- Una fila por documento subido (anverso, reverso, selfie), no una fila por
-- solicitud con varias columnas de referencia. Esto es lo que resuelve el
-- pendiente de diseno anotado desde la subtarea 3: el contrato de
-- kyc-service.openapi.yaml solo aceptaba una clave de objeto por
-- verificacion. Con documento_kyc modelado asi, /kyc/verify pasa a pedir
-- las dos claves (anverso y reverso) que ya existen como filas separadas
-- aqui, en vez de forzar un array o una convencion de nombres en MinIO.
--
-- Solo la referencia se guarda en PostgreSQL: object_key, hash_sha256,
-- mime_type, tamano. La imagen en si nunca atraviesa este servicio ni se
-- guarda en la base (diseno-base.md §4.1).
--
-- Sin columna "vigente" ni limite de filas por tipo a proposito: la subtarea
-- 11 (limite de tres intentos y derivacion a revision manual) es quien
-- decide como se filtran los reintentos. Anadir esa logica aqui adelantaria
-- una decision que no le corresponde a esta migracion.

CREATE TABLE documento_kyc (
    id              UUID         PRIMARY KEY,
    solicitud_id    UUID         NOT NULL,
    tipo_documento  VARCHAR(16)  NOT NULL,
    -- Clave del objeto en MinIO (bucket ayni-kyc-documentos), generada por
    -- GenerarUrlDeSubidaService (AYNI-13 subtarea 7): kyc/{solicitudId}/{tipo}-{uuid}.{extension}
    object_key      VARCHAR(255) NOT NULL,
    -- Hexadecimal en minuscula, calculado por MinioAlmacenDeDocumentos
    -- (AYNI-13 subtarea 8). Permite detectar alteracion del objeto.
    hash_sha256     VARCHAR(64)  NOT NULL,
    mime_type       VARCHAR(64)  NOT NULL,
    tamano_bytes    BIGINT       NOT NULL,
    subido_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_documento_solicitud
        FOREIGN KEY (solicitud_id) REFERENCES solicitud_onboarding (id) ON DELETE CASCADE,
    CONSTRAINT ck_documento_tipo
        CHECK (tipo_documento IN ('ANVERSO', 'REVERSO', 'SELFIE')),
    CONSTRAINT ck_documento_hash_sha256
        CHECK (hash_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_documento_tamano_positivo
        CHECK (tamano_bytes > 0)
);

CREATE INDEX ix_documento_solicitud ON documento_kyc (solicitud_id);

COMMENT ON TABLE documento_kyc IS
    'Referencia a cada documento KYC subido a MinIO. Una fila por documento, '
    'no una fila por solicitud: asi el contrato de kyc-service puede pedir '
    'anverso y reverso como dos claves distintas.';
COMMENT ON COLUMN documento_kyc.object_key IS
    'Clave del objeto en MinIO. La imagen en si nunca se guarda aqui ni '
    'atraviesa la API — ver diseno-base.md §4.1.';
COMMENT ON COLUMN documento_kyc.hash_sha256 IS
    'SHA-256 en hexadecimal del contenido del objeto, calculado al momento '
    'de la subida. Permite detectar alteracion posterior del objeto.';
