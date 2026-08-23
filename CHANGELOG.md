# Registro de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).
Este proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

Las entradas se generan automáticamente a partir de los mensajes de commit, por lo que respetar
Conventional Commits no es una formalidad: es lo que mantiene este archivo con sentido.

## [Sin publicar]

### Añadido
- Estructura inicial del monorepo con arquitectura hexagonal.
- Documentos guía: contribución, estilo de código, seguridad, hoja de ruta y Definition of Done.
- Registro de decisiones arquitectónicas (ADR-0001 a ADR-0006).
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

### Seguridad
- Elevado el BOM a Spring Boot 3.5.16 y Spring Cloud 2025.0.3, y fijado Netty 4.1.136.Final, tras
  detectar Trivy 4 vulnerabilidades críticas y 95 altas en las imágenes de los servicios Java.
- Elevadas las dependencias de `ayni-kyc-service` para corregir la falsificación de peticiones en
  Starlette y la escritura arbitraria de ficheros en `python-multipart`.
