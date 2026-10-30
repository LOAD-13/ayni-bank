# Plan de pruebas del sistema · Ayni Bank

**Historia:** AYNI-219 (T-19, capítulo 10 del informe del APF3) · **Versión del sistema:** release 2.0
en `develop` · **Norma de referencia:** estructura de ISO/IEC/IEEE 29119-3 (plan de pruebas), calidad
según ISO/IEC 25010:2023.

Anexos: [listado de casos de prueba](casos-de-prueba.md) (generado del código) ·
[reporte de calidad funcional](reporte-calidad-funcional.md) (capítulo 12).

---

## 1. Objetivo y alcance

Comprobar que Ayni Bank hace lo que prometen sus historias de usuario, que lo hace sin errores y que
mantiene las reglas que no se negocian en un banco: el saldo es la suma de los asientos, los importes
son exactos, toda operación monetaria es idempotente y ningún dato sensible acaba en un log.

| Dentro del alcance | Fuera del alcance (y por qué) |
|---|---|
| Los seis componentes: `ayni-web`, `ayni-gateway`, `ayni-identity-service`, `ayni-core-banking-service`, `ayni-notification-service`, `ayni-kyc-service` | Pruebas de carga sostenida: son HU-14 (Sprint 8) |
| Las historias funcionales HU-01 a HU-08, HU-13 y HU-21, que están en «Listo» | HU-09 a HU-12 y HU-22: siguen en desarrollo (Sprint 5 y siguientes) |
| Seguridad automatizada (secretos, dependencias, imágenes, DAST) | Prueba de penetración manual completa |
| Accesibilidad WCAG 2.1 AA y usabilidad ISO 25010 | Pruebas con usuarios reales: son el UAT de T-26 |

## 2. Estrategia: niveles de prueba

La pirámide se respeta: muchas pruebas rápidas abajo, pocas y caras arriba. Cada nivel responde a una
pregunta distinta y ninguno sustituye a otro.

| Nivel | Qué pregunta responde | Cómo |
|---|---|---|
| **Unitaria** | ¿Esta pieza hace lo que dice, incluidos los errores y los límites? | Dominio sin Spring (DoD); casos de uso con dobles en memoria; componentes React aislados |
| **Arquitectura** | ¿Se respeta la hexagonal? | ArchUnit: el dominio no importa Spring, JPA ni infraestructura; los puertos se llaman `*Port` y `*UseCase` |
| **Integración** | ¿Las piezas reales encajan? | Testcontainers con PostgreSQL 16 y Redis reales; *slices* de Spring (`@WebMvcTest`); `TestClient` de FastAPI |
| **Aceptación** | ¿Se cumplen los escenarios DADO/CUANDO/ENTONCES de la HU? | Cucumber en español contra PostgreSQL de Testcontainers, con la misma configuración que producción |
| **Navegador (E2E)** | ¿El cliente puede hacerlo en la web, en escritorio y en celular? | Playwright con Chromium en dos proyectos (1280 px y 390 px) |
| **Accesibilidad y usabilidad** | ¿Lo puede usar cualquiera? | axe-core (WCAG 2.1 AA) en cada ruta; suite de usabilidad ISO 25010 (T-21) |
| **Seguridad** | ¿Hay secretos, CVE o fallos explotables? | Gitleaks, Trivy, SonarCloud; OWASP ZAP, Nikto, Nmap y testssl.sh contra el entorno desplegado |
| **Humo** | ¿Lo que se acaba de desplegar responde? | `pruebas-humo.sh` tras cada despliegue: salud, versión, HTTPS, cabeceras, 400 y 401 |

**Criterio de diseño de casos.** Particiones de equivalencia y valores límite en todo lo que tiene
reglas (montos, contraseñas, DNI, límites de tarjeta y de tasa). Transición de estados en los flujos
con estados (solicitud KYC, tarjeta, sesión). Cada escenario de aceptación de Jira se traduce a
Gherkin sin cambiarle el sentido.

## 3. Frameworks y herramientas

| Componente | Lenguaje | Unitarias e integración | Aceptación | Cobertura |
|---|---|---|---|---|
| identity · core-banking · notification · gateway | Java 21 · Spring Boot 3.5 | JUnit 5, Mockito, AssertJ, Spring Test, Testcontainers, ArchUnit 1.3 | Cucumber 7.22 | JaCoCo 0.8.12 |
| kyc | Python 3.12 · FastAPI | pytest 8.3, httpx (`TestClient`); ruff y mypy `--strict` | Gherkin de referencia (`tests/onboarding_kyc.feature`) | pytest-cov 5.0 |
| web | TypeScript · Next.js 15.5 · React 19 | Vitest 3.2, Testing Library | Playwright 1.62 + axe-core 4.13 | V8 (Vitest) |

**Calidad y seguridad transversal:** SonarCloud (puerta de calidad en cada PR), Gitleaks (secretos en
todo el historial), Trivy (CVE críticas y altas en cada imagen), OWASP ZAP *baseline*, Nikto, Nmap y
testssl.sh (flujo `seguridad.yml` contra el entorno desplegado).

## 4. Entorno y datos de prueba

- **Integración continua:** GitHub Actions en `ubuntu-latest` (Docker disponible para Testcontainers).
  Cada PR ejecuta solo los trabajos cuyo código cambió (trabajo «Detectar cambios»). El *check*
  obligatorio «CI completa» espera a todos.
- **Bases de datos:** un PostgreSQL 16 desechable por clase de prueba, con las migraciones reales de
  Flyway. Nunca H2: lo que se prueba es el mismo motor que producción.
- **Datos:** sintéticos, generados por la propia prueba (titulares y cuentas nuevas en cada caso).
  Las imágenes de DNI son sintéticas (`tests/e2e-hu02/generar_dni_sintetico.py`). Ningún dato
  personal real entra en el repositorio.
- **Local:** `./mvnw verify`, `pytest`, `npm run test` y `npm run test:e2e`, con los mismos comandos
  que la CI.

## 5. Criterios de entrada, salida y suspensión

| | Criterio |
|---|---|
| **Entrada** | La HU tiene criterios de aceptación en Jira; la rama compila; los escenarios están escritos en Gherkin antes de cerrar la HU |
| **Salida (por PR)** | «CI completa» en verde: todas las pruebas pasan, no solo las nuevas · SonarCloud aprobado con **cobertura ≥ 80 % del código nuevo** · Gitleaks sin hallazgos · Trivy sin CVE críticas ni altas · axe-core sin violaciones · una aprobación de otra persona |
| **Salida (por release)** | Lo anterior en `develop`, pruebas de humo en verde tras desplegar y escenarios de aceptación de las HU del release en verde |
| **Suspensión** | `develop` en rojo: no se fusiona nada más hasta arreglarlo (ocurrió el 26-oct con la CVE de Next.js, PR #82) |
| **Brecha conocida** | Un criterio que el código todavía no cumple se escribe como escenario `@pendiente`: queda a la vista y no se ejecuta |

## 6. Casos de prueba

El listado completo, con identificador, nivel, nombre y archivo de cada caso, está en
[`casos-de-prueba.md`](casos-de-prueba.md). Se genera del código con
`python infra/scripts/listar-casos-de-prueba.py`, así que no se desactualiza. Resumen de los casos
**especificados** (un caso parametrizado cuenta una vez):

<!-- resumen-casos -->
| Componente | Unitaria | Arquitectura | Integración | Aceptación | Navegador | Usabilidad | Total |
|---|---:|---:|---:|---:|---:|---:|---:|
| identity | 464 | 5 | 23 | 31 | — | — | **523** |
| core-banking | 191 | 5 | 19 | 30 | — | — | **245** |
| notification | 24 | 5 | 6 | 5 | — | — | **40** |
| gateway | 32 | — | 10 | — | — | — | **42** |
| kyc | 59 | — | 25 | — | — | — | **84** |
| web | 165 | — | — | — | 38 | 23 | **226** |
| **Total** | **935** | **15** | **83** | **66** | **38** | **23** | **1 160** |
<!-- /resumen-casos -->

## 7. Ejecución y resultados

Ejecución de referencia: **CI del PR de la release 2.0.0, ejecución
[40967550502](https://github.com/LOAD-13/ayni-bank/actions/runs/40967550502)** (commit `b8c9678`,
29-oct-2026). Los casos parametrizados y los de navegador cuentan una vez por ejecución (los de
Playwright corren en escritorio y en celular), por eso hay más ejecuciones que casos.

<!-- resultados -->
| Suite | Ejecutadas | Pasan | Fallan | Omitidas |
|---|---:|---:|---:|---:|
| Java · identity | 596 | 596 | 0 | 0 |
| Java · core-banking | 255 | 255 | 0 | 0 |
| Java · notification | 40 | 40 | 0 | 0 |
| Java · gateway | 45 | 45 | 0 | 0 |
| Python · kyc (pytest; sin cambios desde la ejecución del 27-oct) | 92 | 92 | 0 | 0 |
| Web · unitarias (Vitest, 28 archivos) | 167 | 167 | 0 | 0 |
| Web · navegador, accesibilidad y usabilidad (Playwright) | 216 | 215 | 0 | 1 |
| **Total** | **1 411** | **1 410** | **0** | **1** |
<!-- /resultados -->

La omisión de Playwright es deliberada: el tamaño táctil mínimo (WCAG 2.5.8) solo se mide en el
proyecto de celular.

**Evidencia** (capturas de los informes de esa ejecución, en [`evidencia/`](evidencia/)):

| Evidencia | Archivo |
|---|---|
| Cobertura JaCoCo de cada servicio Java | `evidencia/jacoco-*.png` |
| Cobertura de kyc (coverage.py) | `evidencia/cobertura-kyc.png` |
| Cobertura de la web (Vitest, V8) | `evidencia/cobertura-web.png` |
| Informe de Playwright | `evidencia/playwright.png` |
| Resumen de la ejecución en GitHub Actions | `evidencia/ci-develop.png` |

## 8. Cobertura

<!-- cobertura -->
| Componente | Líneas | Ramas | Fuente |
|---|---:|---:|---|
| identity | 94,4 % | 83,1 % | JaCoCo |
| core-banking | 96,9 % | 86,1 % | JaCoCo |
| notification | 99,0 % | 100 % | JaCoCo |
| gateway | 97,5 % | 87,5 % | JaCoCo |
| kyc | 94,8 % | — | coverage.py |
| web · total | 57,9 % | 86,4 % | V8 (Vitest) |
<!-- /cobertura -->

JaCoCo excluye lo que no tiene lógica (clases de arranque, configuración, puertos, entidades, DTO y
el cliente generado de OpenAPI). En la web, el total baja por la portada (`componentes/landing`, 0 %
en Vitest): es contenido estático que cubren las pruebas de navegador y de accesibilidad, que no
cuentan para la cobertura de Vitest. Las páginas (`app/`) también se ejercitan sobre todo con Playwright. La puerta de SonarCloud exige el 80 % sobre el **código nuevo** de cada PR, y se cumple.

## 9. Riesgos del plan

| Riesgo | Mitigación |
|---|---|
| Una prueba E2E depende de la red o del tiempo y falla de forma intermitente | Las respuestas de la API se simulan en Playwright (`page.route`); las esperas son por estado, no por tiempo |
| Testcontainers no disponible en una máquina | La CI siempre tiene Docker; en local basta Docker Desktop, que ya es requisito del entorno (README) |
| La cobertura total de la web no refleja lo probado por Playwright | Se informa por carpeta (apartado 8) y no como una única cifra |
| Los escenarios de HU-03, HU-04 y HU-07 aún no están automatizados en Gherkin | Cubiertos por pruebas unitarias y de integración. El Gherkin de HU-04 y HU-07 es AYNI-229 y AYNI-231; el de HU-03 existe como referencia (`tests/onboarding_kyc.feature`) sin automatizar |
