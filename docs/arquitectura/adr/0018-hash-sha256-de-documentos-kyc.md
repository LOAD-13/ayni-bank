# ADR-0018 · Cálculo del hash SHA-256 de documentos KYC en ambos lados

- **Estado:** aceptado
- **Fecha:** 12 de septiembre de 2026
- **Historias afectadas:** HU-02 (verificación de identidad)
- **Reemplaza a:** ninguno (corrige una afirmación técnica de
  [ADR-0017](0017-url-pre-firmada-de-subida-con-minio-sdk.md), ver sección "Corrección" abajo)

## Contexto

La subtarea 8 de AYNI-13 pide calcular y almacenar el hash SHA-256 de cada documento KYC
(`diseno-base.md` §4.1: *"En PostgreSQL solo se guarda la referencia: object_key, hash_sha256,
mime_type, tamaño... El hash SHA-256 permite detectar alteración del objeto"*).

Esta capacidad toca dos lados sin conectar hasta ahora:

- **Python** (`ayni-kyc-service`): `AlmacenObjetosPort.calcular_hash(clave_objeto) -> str` está
  declarado desde el esqueleto inicial del Sprint 1, pero sin ninguna implementación real — las
  subtareas 3, 4 y 5 solo usaron un `AlmacenObjetosFake` en tests. La carpeta
  `infrastructure/storage/` seguía vacía.
- **Java** (`ayni-identity-service`): la columna `hash_sha256` vive en `documento_kyc`
  (schema `identity`), pero esa tabla todavía no existe — es la migración de la subtarea 9.

## Decisión

**Se implementa el cálculo en ambos lados; la persistencia en base de datos queda para la
subtarea 9**, cuando exista la tabla que la reciba.

**Python — `AlmacenObjetosMinIO`** (`infrastructure/storage/almacen_objetos_minio.py`):
primera implementación real de `AlmacenObjetosPort` en el servicio, conectando por fin a MinIO
los tres adaptadores ya construidos (`DetectorDocumentoOpenCV`, `ValidadorCalidadOpenCV`,
`ExtractorDatosPaddleOCR`), que hasta ahora solo se habían probado con fakes.
`descargar()` usa `Minio.get_object()` (`minio==7.2.9`, ya en `requirements.txt` desde el
esqueleto inicial); `calcular_hash()` es `hashlib.sha256(self.descargar(...)).hexdigest()`.

**Java — `MinioAlmacenDeDocumentos.calcularHash()`**: se añade a `AlmacenDeDocumentosPort` junto
al `generarUrlDeSubida()` de la subtarea 7. Usa `MinioClient.getObject()` +
`MessageDigest.getInstance("SHA-256")` + `HexFormat.of().formatHex(...)` — mismo patrón que
`EmisorDeTokensJwt.huellaDe()` ya usa para la huella del refresh token, salvo la codificación:
aquí se usa **hexadecimal**, no Base64, porque el hash debe ser comparable byte a byte con el
que calcula Python sobre el mismo objeto, y hexadecimal es el formato convencional para SHA-256
(el mismo que produce `sha256sum`).

**No se persiste nada todavía.** Ninguno de los dos lados escribe en base de datos: el método
`calcularHash`/`calcular_hash` solo calcula y retorna el valor. La subtarea 9 (migración
`documento_kyc`) es quien decide dónde y cuándo se guarda.

## Corrección de un hallazgo del ADR-0017

Al implementar el adaptador Java, los tests existentes de `generarUrlDeSubida` (de la subtarea
7) empezaron a fallar con `ConnectException: Connection refused`, contradiciendo la afirmación
del ADR-0017 de que *"getPresignedObjectUrl no hace ninguna llamada de red a MinIO"*.

Esa afirmación era imprecisa: el **cálculo de la firma en sí** es local (HMAC-SHA256), pero si
el `MinioClient` no tiene una región fijada explícitamente, el SDK intenta **resolverla con una
llamada de red** (`GetBucketLocation`) antes de firmar. Los tests de la subtarea 7 habían
pasado porque, en aquel momento, algo respondía en `localhost:9000`; en una sesión posterior,
sin nada escuchando ahí, el mismo código falló — revelando la dependencia de red oculta.

**Corrección aplicada**: se fija `.region("us-east-1")` explícitamente al construir el
`MinioClient`, tanto en `ConfiguracionDeMinio` (producción) como en el test. MinIO no es
multi-región, así que cualquier valor fijo sirve — se elige `us-east-1` por ser el default más
común en herramientas S3-compatibles. Con la región fijada, la firma es genuinamente local y
los tests no dependen de que haya un MinIO real escuchando. El comentario de la clase
(`MinioAlmacenDeDocumentos`) y el ADR-0017 se corrigieron para reflejar esto.

## Alternativas evaluadas

**Persistir el hash ya en esta subtarea, en una tabla provisional.** Se descartó: crear una
tabla de paso solo para esta subtarea, que la 9 tendría que migrar o descartar, es trabajo
doble sin beneficio — la 9 ya define el modelo de datos completo de `documento_kyc`.

**Calcular el hash solo en un lado (Python o Java) y pasarlo al otro por el contrato.** Se
descartó por ahora: el contrato de `kyc-service.openapi.yaml` no tiene ningún campo para esto
(mismo tipo de gap ya señalado para anverso/reverso, ver plan de HU-02), y resolverlo aquí
adelantaría diseño de contrato que no es el foco de esta subtarea. Ambos lados calculan el hash
de forma independiente sobre el mismo objeto en MinIO; en el futuro se puede decidir si uno
usa el valor calculado por el otro o si ambos siguen calculando por separado como verificación
cruzada.

## Consecuencias

**A favor**

- Python conecta por primera vez a un MinIO real: deuda técnica de las subtareas 3-5 resuelta.
- El hallazgo de la región corrige una afirmación incorrecta antes de que se propagara más
  (por ejemplo, a la subtarea 10, que envuelve estas llamadas con Resilience4j — un timeout
  mal calibrado sobre una llamada que "no debería hacer red" habría sido más difícil de
  diagnosticar más adelante).
- Ambos cálculos usan hexadecimal: comparables directamente sin conversión.

**En contra**

- El hash calculado en esta subtarea no se usa ni se verifica en ningún flujo real todavía
  (ni Python ni Java lo invocan desde un caso de uso o endpoint) — queda como capacidad
  disponible para cuando la subtarea 9 (y las de integración, 10-11) conecten el flujo
  completo.
- Sigue sin resolverse cómo se comunican el hash calculado por Python y el calculado por Java
  entre sí (o si hace falta que se comuniquen). Documentado como pendiente.

## Pendiente

- Decidir en la subtarea 9 si el hash se calcula una vez (¿en qué lado?) y se persiste, o si
  ambos lados lo calculan de forma independiente para una verificación cruzada de integridad.
- Conectar `AlmacenObjetosMinIO` (Python) a la inyección de dependencias real de
  `DetectorDocumentoOpenCV`/`ValidadorCalidadOpenCV`/`ExtractorDatosPaddleOCR` — hoy siguen sin
  usarse desde ningún endpoint FastAPI real (`main.py` solo expone `/health` y `/metrics`).
