# ADR-0019 · La tabla `documento_kyc` (una fila por documento) resuelve el gap de anverso/reverso

- **Estado:** aceptado
- **Fecha:** 12 de septiembre de 2026
- **Historias afectadas:** HU-02 (verificación de identidad)
- **Reemplaza a:** ninguno (resuelve el pendiente anotado en el plan de HU-02 desde la
  subtarea 3, sobre el contrato `kyc-service.openapi.yaml`)

## Contexto

Desde la subtarea 3 quedó anotado un gap: el README y la subtarea 5 ("OCR de anverso y
reverso") confirman que el DNI se verifica por ambas caras, pero el contrato
`kyc-service.openapi.yaml` (AYNI-95) definía `VerificationRequest` con una sola
`identityDocumentKey` — no había forma de enviar dos claves en una misma verificación. Se
decidió entonces no tocar el contrato hasta esta subtarea 9, porque aquí de todas formas hay
que decidir el modelo de datos de `documento_kyc`, y conviene resolver contrato y esquema de
BD en una sola pasada.

## Decisión

**`documento_kyc` es una fila por documento subido (anverso, reverso, selfie), no una fila por
solicitud con varias columnas de referencia.** Esa forma de modelar es lo que resuelve el gap
de forma natural: en vez de forzar un array o una convención de nombres en MinIO para que un
solo objeto represente "las dos caras", cada cara es un objeto y un registro independiente,
vinculados a la misma `solicitud_onboarding` por `solicitud_id`.

Migración `V5__documentos_kyc.sql`:

```sql
CREATE TABLE documento_kyc (
    id              UUID         PRIMARY KEY,
    solicitud_id    UUID         NOT NULL REFERENCES solicitud_onboarding(id) ON DELETE CASCADE,
    tipo_documento  VARCHAR(16)  NOT NULL CHECK (tipo_documento IN ('ANVERSO','REVERSO','SELFIE')),
    object_key      VARCHAR(255) NOT NULL,
    hash_sha256     VARCHAR(64)  NOT NULL CHECK (hash_sha256 ~ '^[0-9a-f]{64}$'),
    mime_type       VARCHAR(64)  NOT NULL,
    tamano_bytes    BIGINT       NOT NULL CHECK (tamano_bytes > 0),
    subido_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

Columnas exactamente las que pide `diseno-base.md` §4.1 (`object_key`, `hash_sha256`,
`mime_type`, tamaño), más `id`, `solicitud_id` y `tipo_documento` para que la tabla sea
autosuficiente. El CHECK de `hash_sha256` fija el formato (64 caracteres hexadecimales en
minúscula) — coherente con lo que producen tanto `AlmacenObjetosMinIO.calcular_hash()`
(Python) como `MinioAlmacenDeDocumentos.calcularHash()` (Java), ambos de la subtarea 8.

**Sin columna "vigente" ni límite de filas por tipo.** La subtarea 11 ("límite de tres
intentos y derivación a revisión manual") es quien decide cómo se filtran los reintentos —
resolverlo aquí adelantaría una decisión que no le corresponde a esta migración. Por ahora,
cada subida es simplemente una fila más.

**Contrato `kyc-service.openapi.yaml` actualizado**: `VerificationRequest` pasa de
`identityDocumentKey` (una clave) a `anversoDocumentKey` + `reversoDocumentKey` (dos claves,
ambas requeridas). El cliente Java generado (subtarea 2, `openapi-generator-maven-plugin`) se
regenera automáticamente en el siguiente build; se actualizó `ClienteKycGeneradoTest` para
usar los nuevos setters. Del lado Python, `ExtractorDatosPaddleOCR.extraer()` (subtarea 5) ya
recibía `clave_objeto_anverso` y `clave_objeto_reverso` como parámetros separados desde su
diseño original, así que no requiere ningún cambio — el contrato solo se pone al día con una
forma que el código Python ya anticipaba correctamente.

## Validación

Sin Testcontainers ni entorno nuevo: se aplicó la migración directamente contra el
`ayni-postgres` real del `docker-compose.yml` local (ya corriendo), dentro de una transacción
de prueba con `ROLLBACK` para no dejar datos ni tablas de prueba en la base compartida:

- `CREATE TABLE` + `CREATE INDEX` + los 3 `COMMENT` se aplicaron sin error de sintaxis.
- Un insert válido pasa.
- Tres inserts inválidos (tipo de documento fuera del enum, hash con formato incorrecto,
  tamaño negativo) fallan con `check_violation`, confirmando que los tres `CHECK` funcionan.
- Borrar la `solicitud_onboarding` borra en cascada sus `documento_kyc` (`ON DELETE CASCADE`
  verificado con un `DELETE` + `COUNT` posterior en 0).

## Alternativas evaluadas

**Una fila por solicitud, con columnas `anverso_object_key`/`reverso_object_key`/etc.
repetidas.** Se descartó: agregar la selfie (HU-03) habría exigido otro juego de columnas, y
cualquier documento adicional futuro (un comprobante, por ejemplo) habría requerido una
migración de esquema en vez de simplemente insertar una fila con un `tipo_documento` nuevo.

**Persistir el hash y conectar el flujo completo (caso de uso "confirmar subida") ya en esta
subtarea.** Se descartó: el alcance de la subtarea 9, según el sprint backlog, es la migración
en sí. Conectar `GenerarUrlDeSubidaService` (subtarea 7) y `MinioAlmacenDeDocumentos.calcularHash()`
(subtarea 8) con un `INSERT` real en `documento_kyc` es trabajo de integración que encaja mejor
en las subtareas 10-11, cuando se decida también qué pasa con el estado de la solicitud y el
límite de reintentos.

## Consecuencias

**A favor**

- El gap de diseño anotado desde la subtarea 3 queda resuelto sin necesitar tocar el contrato
  dos veces.
- El modelo de `documento_kyc` es extensible a nuevos tipos de documento sin migrar el esquema.
- Validado contra Postgres real, no solo revisión visual del SQL.

**En contra**

- Sigue sin existir el repositorio/adaptador Java que inserte en `documento_kyc` — la tabla
  existe pero nada la usa todavía. Es deuda explícita, a resolver en las subtareas 10-11.
- El endpoint `POST /kyc/verify` en Python (`main.py`) tampoco existe todavía: el contrato
  actualizado define la forma de la petición, pero no hay código FastAPI que la reciba.

## Pendiente

- Implementar el repositorio de persistencia Java para `documento_kyc` cuando el caso de uso
  de integración (subtareas 10-11) necesite escribir en la tabla.
- Implementar el endpoint `POST /kyc/verify` en `ayni-kyc-service` (Python), conectando
  `ExtractorDatosPaddleOCR` y el resto de adaptadores de las subtareas 3-6 a una ruta FastAPI
  real.
