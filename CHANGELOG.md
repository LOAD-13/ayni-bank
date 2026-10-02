# Registro de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).
Este proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

Las entradas se generan automáticamente a partir de los mensajes de commit, por lo que respetar
Conventional Commits no es una formalidad: es lo que mantiene este archivo con sentido.

## [Sin publicar]

### Añadido
- Estructura inicial del monorepo con arquitectura hexagonal.
- Documentos guía: contribución, estilo de código, seguridad, hoja de ruta y Definition of Done.
- Registro de decisiones arquitectónicas (ADR-0001 a ADR-0007).
- Sistema de diseño derivado de la identidad visual de la marca.
- Entorno de desarrollo con Docker Compose: PostgreSQL, RabbitMQ, MinIO y observabilidad.
- Pipelines de integración y despliegue continuos en GitHub Actions.
- Esqueleto hexagonal de los cinco servicios, con health checks, reglas de ArchUnit e imágenes
  ARM64 multi-etapa.
- Migraciones de Flyway por schema, aplicadas al arrancar, con los catálogos de roles y permisos,
  productos y tasas, política cambiaria y plantillas de notificación.
- Datos semilla de tipo de cambio, cargados solo bajo el perfil `dev`.
- Fuentes de datos de Grafana aprovisionadas: Prometheus y Loki.
- Umbral de cobertura del dominio del 80 %, exigido por JaCoCo y bloqueante.

- Aplicación web andamiada con Next.js 15, React 19, TypeScript y Tailwind 4, con los tokens de
  marca como única fuente de color y las seis verificaciones que exige el pipeline: linting, formato,
  tipos, pruebas con cobertura, accesibilidad con axe-core y construcción de producción.
- Imagen ARM64 multi-etapa de `ayni-web` sobre la salida `standalone`, ejecutada sin privilegios y
  sin npm, y su reincorporación al entorno de desarrollo: el `docker compose` levanta ya los doce
  contenedores.
- Contrato OpenAPI de `ayni-identity-service` con el registro de visitantes, incluidos los errores
  en formato RFC 7807.
- Dependencias de `ayni-identity-service` para HU-01: JPA, validación declarativa y Argon2id sobre
  Bouncy Castle.
- `.dockerignore` en la raíz, que es el único que Docker consulta cuando el contexto de construcción
  es el repositorio completo.
- Puertos publicados de la web y de Grafana parametrizables, para poder esquivar los rangos que
  Hyper-V reserva en Windows y que cambian en cada reinicio.

- **Registro de usuario (HU-01).** Dominio de identidad sin framework —correo, celular, política de
  contraseñas, consentimiento y estados del usuario—, derivación con Argon2id sobre los parámetros de
  OWASP, persistencia JPA con su mapeador, y `POST /api/v1/registro` con errores en formato RFC 7807.
- Respuesta indistinguible cuando el correo ya está registrado, incluida la paridad de tiempos de
  respuesta. Ver [ADR-0008](docs/arquitectura/adr/0008-respuesta-indistinguible-en-el-registro.md).
- **Identidad declarada en el registro.** Nombres, apellidos, tipo y número de documento y fecha de
  nacimiento se guardan en la solicitud de onboarding como término de comparación del OCR de HU-02;
  el número va cifrado con AES-256-GCM y nunca se devuelve ni se registra en un log. Incluye la
  comprobación de mayoría de edad. Ver
  [ADR-0009](docs/arquitectura/adr/0009-identidad-declarada-antes-del-ocr.md).
- **Landing pública y formulario de registro**, construidos sobre las pantallas aprobadas en pen.dev,
  con la página de funcionalidad pendiente para todo destino que aún no existe.
- Verificación automática del frontend con Playwright sobre navegador real: reflujo entre 360 y
  3440 px, comportamiento ante el zoom, enlaces sin destinos rotos, accesibilidad WCAG 2.1 AA y el
  comportamiento del formulario de registro.
- Migración de `usuario`, `persona` y `solicitud_onboarding` en el schema `identity`.
- Rutas del gateway hacia los tres servicios de negocio y CORS restringido a los orígenes de la
  aplicación web. Hasta ahora el gateway no enrutaba nada.
- Documento de funcionalidades pendientes de la landing, que registra qué promete cada enlace, qué
  historia lo cubre y qué queda fuera del alcance declarado en el Acta.

- **Inicio de sesión seguro (HU-04).** Dos pasos —credenciales y segundo factor— con TOTP
  según RFC 6238 implementado sobre javax.crypto y contrastado con el vector oficial de la
  especificación. Token de acceso JWT de 15 minutos, token de renovación rotativo de 7 días
  en cookie HttpOnly con SameSite=Strict, y detección de reutilización que invalida la
  familia entera de la sesión. Bloqueo progresivo tras cinco intentos fallidos, con techo de
  una hora para que nadie pueda dejar fuera al titular fallando a propósito. Pista de
  auditoría con IP y agente de usuario en tabla propia, no en los logs.
- Alta del segundo factor en el primer ingreso, con el QR generado en el navegador para que
  el secreto no viaje además como imagen.
- **Apertura automática de cuenta de ahorro (HU-05, esqueleto).** Dominio contable con
  importes en BigDecimal y redondeo HALF_EVEN, cuentas sin columna de saldo —es la suma de
  sus asientos por partida doble—, número de cuenta y CCI de 20 dígitos con sus dígitos de
  control. La cuenta se abre al oír por RabbitMQ que la solicitud quedó aprobada, y el
  evento CuentaAperturada se escribe en la bandeja de salida dentro del mismo COMMIT
  (ADR-0003). Idempotente frente a la reentrega del mismo evento.
- Pantalla final del onboarding con el saludo por nombre, la tarjeta de la cuenta, el CCI copiable,
  la TREA leída del catálogo —no escrita en la pantalla— y la proyección de rendimiento calculada a
  partir de ella.
- Endpoint de aprobación manual bajo perfil `dev`, que ocupa el lugar del OCR hasta HU-02, y su ruta
  en el gateway. Ambos se retiran con HU-02.
- Contrato OpenAPI ampliado con los tres endpoints de sesión.
- Pruebas de aceptación en Gherkin, en castellano, con los escenarios de HU-01 tal como los aprobó el
  docente. Se ejecutan en el mismo `mvn test` que el resto.
- Verificación con Playwright del ingreso, de la pantalla final del onboarding y del reflujo de
  `/ingresar` entre 360 y 3440 px.
- Componente `LogotipoAyni`, que pinta la versión oscura del logotipo con una máscara CSS.
- Documento de pendientes para el Sprint 2, con los huecos que no están mapeados en Jira.
- ADR-0010 (segundo factor TOTP y sesión rotativa) y ADR-0011 (el saldo se deriva de los asientos).
- Contrato OpenAPI ampliado con los tres endpoints de sesión.
- Verificación del ingreso y de la pantalla final del onboarding con Playwright, y del reflujo
  de `/ingresar` entre 360 y 3440 px.

### Corregido
- **Surefire no ejecutaba la suite de Cucumber**: solo recoge clases con `Test` en el nombre, de modo
  que los escenarios de las historias existían y el build pasaba en verde sin comprobar ninguno.
- El logotipo era invisible sobre fondo claro. El texto «AYNI Bank» se exportó de pen.dev en blanco
  porque en el prototipo siempre va sobre azul; no era un fallo de maquetación sino de material, y
  por eso ninguna prueba lo detectaba.
- La cookie del token de renovación llevaba el prefijo `__Host-`, que exige `Secure`. En local, sobre
  HTTP, el navegador la descartaba sin avisar: el ingreso respondía 200 y la renovación fallaba
  después sin motivo aparente. El nombre ahora depende del entorno.
- El correo enmascarado repetía un asterisco por carácter oculto, revelando la longitud exacta de la
  parte local. Ahora la máscara mide siempre lo mismo.
- Las clases de `core-banking` estaban en `pe.ayni.bank.corebanking` y no en `pe.ayni.bank.core`, de
  modo que quedaban fuera del escaneo de Spring y de las reglas de ArchUnit, que pasaban en vacío.
- La prueba de reflujo medía el primer titular de la página, que usa `clamp()` a propósito. Una
  tipografía fluida no es el defecto que busca: lo que delata el escalado es que cambie un tamaño
  fijo. Ahora mide `body`.
- El `.env` de la raíz no se cargaba: Compose lo busca junto al fichero compose, de modo que las
  variables quedaban vacías y PostgreSQL no arrancaba. Documentado `--env-file .env`.
- Los contextos de construcción del `docker-compose` apuntaban al directorio de cada servicio en
  lugar de a la raíz del repositorio.
- Faltaba `micrometer-registry-prometheus`, sin el cual `/actuator/prometheus` devuelve 404; y
  `prometheus.yml` recolectaba los cuatro servicios Java en el puerto 8080.
- El umbral de cobertura del dominio estaba declarado en el POM pero ningún plugin lo leía: faltaba
  la ejecución `jacoco:check`, la única que rompe el build.
- El job de imágenes cancelaba el resto de la matriz al primer fallo, ocultando los demás.
- La matriz de imágenes componía la ruta del Dockerfile a partir del nombre del servicio, de modo
  que `ayni-web` —la única imagen que no vive bajo `services/`— quedaba sin construir ni escanear.
  Ahora cada entrada declara su ruta.
- El `.dockerignore` de la raíz excluía `**/out/`, pensado para la exportación de Next.js, y con ello
  borraba del contexto de construcción el paquete `domain/port/out/` de los servicios Java. El código
  compilaba en local y fallaba dentro de la imagen.
- El gateway arrastraba `spring-cloud-gateway-server`, declarado obsoleto y retirado en la próxima
  versión mayor.
- El health check de `ayni-web` daba el contenedor por caído: Node escucha solo en IPv4 y dentro del
  contenedor `localhost` resuelve antes a `::1`.
- Loki publicaba un puerto en el host que nadie usaba —Grafana lo consulta por la red interna— y que
  hacía fallar el arranque completo en Windows.

### Seguridad
- Elevado el BOM a Spring Boot 3.5.16 y Spring Cloud 2025.0.3, y fijado Netty 4.1.136.Final, tras
  detectar Trivy 4 vulnerabilidades críticas y 95 altas en las imágenes de los servicios Java.
- Elevadas las dependencias de `ayni-kyc-service` para corregir la falsificación de peticiones en
  Starlette y la escritura arbitraria de ficheros en `python-multipart`.
- Eliminado el CLI de npm de la imagen de `ayni-web` y elevado OpenSSL sobre la base: Trivy
  reportaba una vulnerabilidad crítica y veintitrés altas, todas del propio npm y de Alpine, ninguna
  de la aplicación. La imagen queda sin hallazgos críticos ni altos.
- Fijado `postcss` por encima de la versión que arrastra Next 15, que acumulaba una vulnerabilidad
  alta de XSS y tres de lectura arbitraria de ficheros.
- Elevado OpenSSL en las imágenes de los cuatro servicios Java: `eclipse-temurin:21-jre-alpine` pasó
  a Alpine 3.24.1 con OpenSSL 3.5.7-r0 y arrastraba CVE-2026-14456. Es deriva de la imagen base, no
  de las dependencias: Trivy informaba cero hallazgos en los jar.
