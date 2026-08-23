# Sprint Backlog — Sprint 0 · Fundación

**17 – 23 de agosto de 2026** · 1 semana · 11 ítems · 56 puntos

## Sprint Goal

> Que el equipo pueda construir: repositorio gobernado, entorno reproducible con un comando,
> verificación automática en cada Pull Request y esqueleto de los cinco servicios en pie.

Es un sprint de andamiaje. No entrega valor al cliente y no debe pretenderlo: entrega **capacidad
de entregar**.

## Capacidad

60 h disponibles (1 semana × 6 personas × ~10 h). Comprometido: 57 h.

---

## Ítems y plan técnico

### `AYNI-32` · T-01 · Acta de Constitución ✅ [5 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Recopilar plantilla del Institute of Project Management y mapear secciones | 1h |
| 2 | Redactar objetivos SMART con indicador de logro | 2h |
| 3 | Definir línea base del alcance: dentro y fuera | 2h |
| 4 | Consolidar costos y escenario comparativo de operación comercial | 1h |
| 5 | Revisión del equipo y cierre de versión | 1h |

### `AYNI-33` · T-02 · Diagrama de Gantt híbrido ✅ [3 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Definir bloques de valor, épicas e hitos | 2h |
| 2 | Generar el diagrama con fases PMBOK, sprints y épicas | 2h |
| 3 | Versionar el cronograma en Mermaid dentro del repositorio | 1h |

### `AYNI-34` · T-03 · Matriz de Gestión de Riesgos ✅ [5 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Identificar riesgos por categoría | 2h |
| 2 | Investigar datos reales del Perú para sustentar probabilidades | 3h |
| 3 | Calcular exposición y clasificar por severidad | 1h |
| 4 | Redactar planes de mitigación y de contingencia | 2h |

### `AYNI-35` · T-04 · Matriz de Gestión de Interesados ✅ [3 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Identificar interesados internos y externos | 1h |
| 2 | Clasificar en la matriz poder-interés | 1h |
| 3 | Definir nivel de interacción con el sistema y derivar roles | 2h |

### `AYNI-36` · T-05 · Product Backlog priorizado y adquisiciones ✅ [3 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Cargar story points en todos los ítems de Jira | 1h |
| 2 | Ordenar el backlog por criterio de valor y riesgo | 1h |
| 3 | Redactar el documento de Product Backlog | 2h |
| 4 | Consolidar el listado de adquisiciones con costos | 1h |

### `AYNI-37` · T-06 · Monorepo con GitFlow ✅ [3 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Inicializar repositorio y estructura de carpetas | 1h |
| 2 | Crear ramas `main` y `develop` con sus reglas de protección | 1h |
| 3 | Configurar `.gitignore` y `.gitattributes` | 1h |
| 4 | Invitar al equipo y verificar permisos | 1h |

### `AYNI-38` · T-07 · Esqueleto hexagonal de los cinco servicios ✅ [8 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Configurar el proyecto Maven multi-módulo con Java 21 y Spring Boot 3 | 3h |
| 2 | Crear la estructura de paquetes hexagonal en los 4 servicios Java | 4h |
| 3 | Implementar las reglas de ArchUnit que verifican las fronteras entre capas | 3h |
| 4 | Andamiar `ayni-kyc-service` con FastAPI y su estructura equivalente | 3h |
| 5 | Escribir el Dockerfile ARM64 de cada servicio | 3h |
| 6 | Exponer health checks en los cinco servicios | 2h |
| 7 | Verificar que los cinco arrancan y responden | 2h |

### `AYNI-39` · T-08 · Identidad visual y design system ✅ [5 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Extraer la paleta cromática de los activos de marca | 1h |
| 2 | Verificar contrastes según WCAG 2.1 AA | 1h |
| 3 | Definir tokens de color, tipografía, espaciado y radios | 2h |
| 4 | Documentar usos permitidos y prohibidos del logotipo | 1h |

### `AYNI-40` · T-09 · Documentos guía del repositorio ✅ [5 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Redactar README con arquitectura, stack y puesta en marcha | 3h |
| 2 | Redactar CONTRIBUTING con GitFlow y convenciones de commit | 2h |
| 3 | Redactar CODESTYLE con la arquitectura y la nomenclatura | 3h |
| 4 | Redactar SECURITY con modelo de amenazas y controles | 2h |
| 5 | Redactar los cinco primeros ADR | 3h |

### `AYNI-29` · HU-18 · [NF] Entorno reproducible con un solo comando ✅ [8 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Escribir `docker-compose.yml` con los 11 servicios y sus health checks | 3h |
| 2 | Crear el script de inicialización de schemas de PostgreSQL | 1h |
| 3 | Configurar la creación automática de buckets privados en MinIO | 1h |
| 4 | Configurar Flyway para aplicar migraciones al arrancar | 2h |
| 5 | Preparar datos de prueba (seed) para operar de inmediato | 3h |
| 6 | Redactar `.env.example` documentando todas las variables | 1h |
| 7 | Verificar arranque en limpio en una máquina sin dependencias previas | 2h |

### `AYNI-30` · HU-19 · [NF] Verificación automática de calidad en cada PR ✅ [8 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Crear el workflow de CI con filtrado por ruta | 3h |
| 2 | Integrar Gitleaks como verificación bloqueante | 1h |
| 3 | Configurar la ejecución de ArchUnit en el pipeline | 2h |
| 4 | Integrar Testcontainers para las pruebas de integración | 2h |
| 5 | Configurar umbral de cobertura con JaCoCo | 1h |
| 6 | Conectar SonarCloud y su quality gate | 2h |
| 7 | Integrar Trivy y OWASP Dependency-Check | 2h |
| 8 | Crear el job agregador exigido por las reglas de protección de rama | 1h |

---

## Estado al cierre · 23 de agosto de 2026

| Estado | Ítems | Puntos |
|---|---|---|
| ✅ Hecho | 11 | 56 |

**Sprint completado: 56 de 56 puntos.**

**Impedimento resuelto.** `AYNI-38` bloqueaba el cierre de `AYNI-29`: sin servicios,
`docker compose up` no podía levantar la aplicación. El esqueleto se fusionó en `develop` el 23 de
agosto y con él quedan en pie los cinco servicios, sus health checks, las reglas de ArchUnit y las
imágenes ARM64.

### Evidencia de `AYNI-29`

Arranque en limpio verificado el 23 de agosto, tras `down -v`, con un solo comando:

```
docker compose --env-file .env -f infra/docker/docker-compose.yml up -d --wait   → exit 0
```

| Comprobación | Resultado |
|---|---|
| Servicios sanos | 12/12 · los 5 de Ayni, PostgreSQL, RabbitMQ, MinIO, Prometheus, Grafana, Loki |
| Migraciones de Flyway | `identity` v1 · `core` v1 · `notification` v1 · repetible de seed — todas OK |
| Datos de referencia | 5 roles · 12 permisos · 17 asignaciones · 4 productos · 2 tasas · 2 segmentos · 9 plantillas |
| Seed de desarrollo | 7 días de tipo de cambio, solo bajo perfil `dev` |
| Buckets privados en MinIO | `ayni-kyc-documentos` · `ayni-estados-cuenta` |
| Objetivos de Prometheus | 5/5 recolectando |
| Vulnerabilidades (Trivy) | 0 ALTAS y 0 CRÍTICAS en los cuatro artefactos Java |

**Cuatro defectos reales encontrados al verificar el arranque en limpio**, ninguno detectable
leyendo el código:

1. Los cinco `build:` del compose apuntaban al directorio del servicio, pero los Dockerfile
   necesitan la raíz del repositorio como contexto.
2. `ayni-web` apuntaba a `web/`, que está vacío: hacía abortar el `up` completo.
3. **El `.env` de la raíz nunca se cargaba.** Compose lo busca junto al fichero compose, así que
   todas las variables se interpolaban a cadena vacía y PostgreSQL se negaba a arrancar. El
   procedimiento documentado en README y CONTRIBUTING era incorrecto desde el primer día.
4. Faltaba `micrometer-registry-prometheus`: exponer `prometheus` en `management.endpoints` no crea
   el endpoint sin él, y los cuatro objetivos Java daban 404. `prometheus.yml` además apuntaba
   todos al puerto 8080 cuando cada servicio escucha en el suyo.

Es el argumento a favor de la subtarea 7: «arranca en mi máquina» y «arranca en limpio» son
verificaciones distintas, y solo la segunda cuenta.

### Lo aprendido, para el Sprint 1

El esqueleto necesitó **ocho rondas de integración continua**, todas por fallos reales y todas
reproducibles en local. Dos acciones concretas salen de aquí:

1. La sección [«Reproducir el pipeline en local»](../../../CONTRIBUTING.md) recoge el comando de
   verificación de cada tecnología. Ejecutarla antes de subir deja de ser opcional.
2. El job `imagenes` usa `fail-fast` por omisión: cuando un servicio de la matriz falla, GitHub
   cancela los otros cuatro y se pierde su resultado. Eso convirtió en ocho rondas secuenciales lo
   que podían haber sido dos o tres. Pendiente para el Sprint 1: `fail-fast: false` y
   `max-parallel: 2`, que además mitiga el `429` de Maven Central.

### Alcance ajustado

- **`ayni-web` sale del `docker-compose` del Sprint 0.** El directorio `web/` está vacío: andamiar
  Next.js implica configurar lint, formato, tipos, pruebas y axe-core, que es trabajo de una
  Historia propia y no del entorno reproducible. Se reincorpora al levantar la aplicación web.
- **Las puertas de calidad se activan de forma escalonada.** Ver
  [ADR-0006](../../arquitectura/adr/0006-activacion-escalonada-de-las-puertas-de-calidad.md).

## Definition of Done

Aplica la de [`DEFINITION_OF_DONE.md`](../../../DEFINITION_OF_DONE.md) en su totalidad.
