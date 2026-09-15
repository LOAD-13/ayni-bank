# ADR-0017 · URL pre-firmada de subida con el SDK Java de MinIO

- **Estado:** aceptado
- **Fecha:** 6 de septiembre de 2026
- **Historias afectadas:** HU-02 (verificación de identidad)
- **Reemplaza a:** ninguno

## Contexto

La subtarea 7 de AYNI-13 pide implementar la subida de documentos KYC a MinIO con URL
pre-firmada desde el navegador. El diseño ya estaba fijado en `diseno-base.md` §3.4/§4.1
("el navegador sube a MinIO con URL pre-firmada... URLs pre-firmadas de 5 minutos"), pero
nada de la implementación existía: ni SDK en `pom.xml`, ni endpoint, ni contrato, ni cliente
en el frontend — a diferencia de las subtareas 3-6, que sí heredaban trabajo previo.

**Alcance acordado con el usuario**: solo backend (`identity-service`) que firma la URL, más
el cliente HTTP mínimo en el frontend (`lib/api.ts`). Las pantallas reales de captura de DNI
(cámara, guía visual, carga desde archivo) son las subtareas 12 y 13, todavía no iniciadas —
esta subtarea deliberadamente no las adelanta.

## Decisión

**Arquitectura hexagonal, igual que el resto del servicio:**

- `AlmacenDeDocumentosPort` (dominio) — un método, `generarUrlDeSubida(claveDeObjeto, tipoDeContenido)`. No conoce MinIO ni ningún SDK.
- `MinioAlmacenDeDocumentos` (`infrastructure/out/storage/`) — implementa el puerto con `io.minio:minio:9.0.3`. Los 5 minutos de vigencia (`diseno-base.md` §4.1) se fijan como constante en el adaptador, no como parámetro: es una decisión de arquitectura ya tomada, no una variable de negocio por llamada.
- `GenerarUrlDeSubidaUseCase` / `GenerarUrlDeSubidaService` — valida que la solicitud exista (reutilizando `RepositorioDeSolicitudesPort.titularDe`, ya usado por `AprobarSolicitudService` con el mismo propósito) y construye la clave de objeto: `kyc/{solicitudId}/{tipo}-{uuid}.{extension}`.
- `DocumentoKycController` — `POST /api/v1/solicitudes/{solicitudId}/documentos/url-de-subida`, `200 OK` (no `202`: a diferencia del registro, aquí no hay ningún proceso asíncrono pendiente — es una consulta computada que devuelve un dato utilizable de inmediato).

**Nueva excepción, no reutilizar `SolicitudNoAprobableException`.** Ese nombre significa
literalmente "no se puede aprobar", que no describe lo que pasa aquí (no se está aprobando
nada). Se introduce `SolicitudNoExisteException`, con el mismo tratamiento (`404` vía
`@ExceptionHandler` local en el controlador, igual que `AprobacionController`).

**El `tipoDeContenido` no se usa en la firma de la URL en sí.** MinIO permite forzar un
`Content-Type` esperado en una URL pre-firmada, pero eso obligaría a que el `PUT` real del
navegador coincida exactamente con ese valor — complica el cliente sin beneficio claro en
esta subtarea. Se deja que el navegador declare su propio `Content-Type` en el `PUT`; el
parámetro queda disponible en el puerto para uso futuro (armar la extensión del objeto, y más
adelante el registro en `documento_kyc`, subtarea 9).

## Hallazgo técnico: `okhttp` es Kotlin Multiplatform, y Maven no resuelve su variante JVM

Al añadir `io.minio:minio:9.0.3` (que depende de OkHttp 5.x), la compilación fallaba con
`cannot access okhttp3.HttpUrl — class file for okhttp3.HttpUrl not found`, pese a que
`mvn dependency:tree` mostraba `okhttp:5.3.2:compile` presente.

Causa real: desde su versión 5.x, OkHttp es un proyecto **Kotlin Multiplatform**. El artefacto
`com.squareup.okhttp3:okhttp` que Gradle resuelve automáticamente a la variante correcta (JVM,
Android, JS...) mediante *Gradle Module Metadata* es, para Maven — que no entiende ese
mecanismo —, un JAR casi vacío (solo `META-INF/`, sin una sola clase). Las clases reales viven
en el artefacto separado `com.squareup.okhttp3:okhttp-jvm`.

**Solución**: excluir `okhttp` de la dependencia transitiva de `io.minio:minio` y declarar
`okhttp-jvm:5.3.2` explícitamente:

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>9.0.3</version>
    <exclusions>
        <exclusion>
            <groupId>com.squareup.okhttp3</groupId>
            <artifactId>okhttp</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp-jvm</artifactId>
    <version>5.3.2</version>
</dependency>
```

Se confirmó con `javap` sobre el JAR real descargado (no solo confiando en documentación)
que la API resultante (`MinioClient.Builder.endpoint/credentials`,
`GetPresignedObjectUrlArgs.Builder.method/bucket/object/expiry`, `Http.Method.PUT`) coincide
exactamente con lo usado en el código.

## Alternativas evaluadas

**Testcontainers con un MinIO real para probar el adaptador.** Se descartó para esta subtarea:
el cálculo de la firma en sí es local (HMAC-SHA256); la única llamada de red que
`getPresignedObjectUrl` podría hacer es para resolver la región del bucket, y se evita
fijándola explícitamente en el `MinioClient` (`.region("us-east-1")`, ver ADR-0018 §Corrección).
Con la región fijada, un contenedor real no verificaría nada que un test contra `MinioClient`
con credenciales de prueba no verifique ya (mismo patrón que `EmisorDeTokensJwtTest`, que
prueba firma JWT sin infraestructura externa). Testcontainers encaja mejor cuando exista una
subida real end-to-end
que probar (subtareas 12/13).

**Reutilizar `SolicitudNoAprobableException`.** Habría evitado una quinta excepción de dominio
casi idéntica, pero el nombre no encaja semánticamente con "generar una URL de subida". Se
prefirió la claridad del nombre sobre el ahorro de una clase de 6 líneas.

## Consecuencias

**A favor**

- Firma 100% local: no depende de que MinIO esté disponible en build/test time.
- La clave de objeto (`kyc/{solicitudId}/{tipo}-{uuid}.{extension}`) es legible y trazable
  a la solicitud sin necesitar todavía la tabla `documento_kyc`.
- `docker-compose.yml` corregido: `ayni-identity-service` ahora depende de
  `minio-init: service_completed_successfully`, cerrando una condición de carrera donde el
  bucket podía no existir aún cuando el servicio generaba URLs.

**En contra**

- La dependencia de `okhttp-jvm` explícita es frágil ante actualizaciones futuras de
  `io.minio:minio`: si el SDK sube de versión y cambia su versión de OkHttp, hay que
  actualizar la exclusión/versión a mano. Documentado en el comentario del `pom.xml`.
- Sin protección de sesión (mismo estado que el resto del servicio hoy: HU-07/gateway con
  JWT no existe todavía). No es una laguna nueva de esta subtarea, es general al servicio.
- Los defaults de `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` en `application.yml` repiten los de
  `.env.example` por la misma razón que `AYNI_CIFRADO_CLAVE`: Compose exporta variable vacía
  si no está en `.env`, y una cadena vacía sustituye el default de Spring en vez de activarlo.

## Pendiente

- Conectar el frontend (`solicitarUrlDeSubida` + `subirDocumento`, ya en `lib/api.ts`) a una
  pantalla real de captura — subtareas 12 y 13.
- Registrar el documento subido en `documento_kyc` y actualizar `estado`/`paso_actual` de la
  solicitud — subtareas 8 y 9. Este endpoint no lo hace: es deliberadamente aislado del
  estado de la solicitud, para no adelantar trabajo de esas subtareas.
- Resolver el gap de anverso+reverso en el contrato de `kyc-service.openapi.yaml`
  (ver nota "Pendiente de diseño" en el plan de HU-02) al llegar a la subtarea 9.
