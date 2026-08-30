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
- Migración de `usuario`, `persona` y `solicitud_onboarding` en el schema `identity`.
- Rutas del gateway hacia los tres servicios de negocio y CORS restringido a los orígenes de la
  aplicación web. Hasta ahora el gateway no enrutaba nada.
- Documento de funcionalidades pendientes de la landing, que registra qué promete cada enlace, qué
  historia lo cubre y qué queda fuera del alcance declarado en el Acta.

### Corregido
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
