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

### `AYNI-36` · T-05 · Product Backlog priorizado y adquisiciones 🟡 [3 pts]

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

### `AYNI-38` · T-07 · Esqueleto hexagonal de los cinco servicios ⬜ [8 pts]

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

### `AYNI-29` · HU-18 · [NF] Entorno reproducible con un solo comando 🟡 [8 pts]

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

## Estado al 15 de agosto de 2026

| Estado | Ítems | Puntos |
|---|---|---|
| ✅ Hecho | 8 | 37 |
| 🟡 En curso | 2 | 11 |
| ⬜ Por hacer | 1 | 8 |

**Impedimento identificado:** `AYNI-38` (esqueleto hexagonal) bloquea el cierre de `AYNI-29`. Sin
servicios, `docker compose up` no puede levantar la aplicación y la historia no cumple su criterio
de aceptación. Es el primer trabajo del equipo.

## Definition of Done

Aplica la de [`DEFINITION_OF_DONE.md`](../../../DEFINITION_OF_DONE.md) en su totalidad.
