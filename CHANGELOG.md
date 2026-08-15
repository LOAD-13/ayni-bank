# Registro de cambios

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/).
Este proyecto sigue [Versionado Semántico](https://semver.org/lang/es/).

Las entradas se generan automáticamente a partir de los mensajes de commit, por lo que respetar
Conventional Commits no es una formalidad: es lo que mantiene este archivo con sentido.

## [Sin publicar]

### Añadido
- Estructura inicial del monorepo con arquitectura hexagonal.
- Documentos guía: contribución, estilo de código, seguridad, hoja de ruta y Definition of Done.
- Registro de decisiones arquitectónicas (ADR-0001 a ADR-0005).
- Sistema de diseño derivado de la identidad visual de la marca.
- Entorno de desarrollo con Docker Compose: PostgreSQL, RabbitMQ, MinIO y observabilidad.
- Pipelines de integración y despliegue continuos en GitHub Actions.
