# Ayni Bank — Documento de Diseño Base

**Proyecto:** Ayni Bank — Banca 100% Digital
**Curso:** Curso Integrador II: Software (100000S12F) — UTP, Ciclo 2026-2
**Fecha:** 14 de agosto de 2026
**Estado:** Aprobado
**Autor del documento:** Joaquín Alfonso Loa Denegri (Product Owner)

Este documento es la fuente de verdad del diseño del proyecto. Toda decisión posterior que lo
contradiga debe registrarse como ADR en `docs/arquitectura/adr/` con su justificación.

---

## 1. Contexto y visión

### 1.1 Problema de negocio (AS-IS)

En Perú, acceder a una cuenta de ahorro impone tres fricciones que excluyen precisamente a quien
tiene menos margen:

1. **Presencialidad obligatoria.** Abrir una cuenta exige acudir a una agencia en horario bancario,
   lo que supone pedir permiso en el trabajo y desplazarse.
2. **Rendimiento nulo.** Las cuentas de ahorro tradicionales rinden en torno al 0.5 % TREA, muy por
   debajo de la inflación, de modo que el ahorrista pierde poder adquisitivo por ahorrar.
3. **Comisiones erosivas.** El mantenimiento mensual consume proporcionalmente más cuanto menor es
   el saldo, penalizando los saldos pequeños.

### 1.2 Solución propuesta (TO-BE)

**Ayni Bank** es un banco 100 % digital para personas naturales en Perú: sin agencias, sin cajeros
propios y sin comisión de mantenimiento. La cuenta se abre en minutos desde el navegador mediante
DNI y selfie, el saldo devenga interés todos los días, y la tarjeta se controla íntegramente desde
la web.

### 1.3 La marca

*Ayni* es el principio andino de reciprocidad: **"hoy por ti, mañana por mí"**. Un banco sin
comisiones, donde el dinero de la comunidad rinde para la comunidad, es literalmente ayni.

| Contexto | Forma |
|---|---|
| Marca / wordmark | `ayni` (minúsculas) |
| Nombre completo | **Ayni Bank** |
| Uso institucional | *Ayni Bank — Banco 100% Digital* |
| Repositorio | `ayni-bank` |
| Paquetes Java | `pe.ayni.bank.<contexto>` |
| Servicios | `ayni-gateway`, `ayni-identity-service`, `ayni-core-banking-service`, `ayni-kyc-service`, `ayni-notification-service` |
| Imágenes Docker | `ghcr.io/LOAD-13/ayni-<servicio>:<tag>` |
| Schemas de BD | `identity`, `core`, `notification` |

Se verificó que no existe una fintech peruana registrada con ese nombre. La verificación fue
superficial (ecosistema fintech público, no búsqueda de marca en INDECOPI), suficiente para un
proyecto académico.

---

## 2. Alcance

### 2.1 Dentro del alcance

- Onboarding con KYC: captura de DNI (anverso y reverso), OCR de datos, selfie con prueba de
  vivacidad y cotejo facial.
- Cuenta de ahorro remunerada en **soles** y en **dólares**.
- Tarjeta de débito virtual con controles del cliente.
- Transferencias entre cuentas Ayni.
- Transferencias interbancarias (contra cámara de compensación simulada con contrato definido).
- Conversión de moneda con tipo de cambio consultado a la **API real de SUNAT/BCRP**.
- Notificaciones por correo electrónico.
- Movimientos y estado de cuenta en PDF.
- Landing pública.
- Back-office interno de operaciones con segregación de funciones.

### 2.2 Fuera del alcance (declarado explícitamente en el Acta)

Aplicativo móvil · billetera digital · créditos y tarjetas de crédito · inversiones y depósitos a
plazo · agencias y cajeros físicos · integración real con SBS o con la cámara de compensación ·
emisión de tarjetas físicas.

### 2.3 Productos financieros

**Cuenta Ayni** — cuenta de ahorro remunerada.

- **Devengo diario.** El interés se calcula cada día sobre el saldo de cierre, no mensualmente.
  Fórmula: `interés_día = saldo_cierre × ((1 + TEA)^(1/360) − 1)`, con año comercial de 360 días
  conforme al uso del sistema financiero peruano. Capitalización mensual.
- **TREA y TEA.** La SBS obliga a publicar la **TREA** (Tasa de Rendimiento Efectivo Anual), que
  refleja lo que el cliente gana realmente descontadas comisiones y considerando la capitalización.
  Como Ayni no cobra mantenimiento, TREA = TEA; la interfaz lo muestra explícitamente como
  argumento comercial.
- **Precisión monetaria.** Todo importe en `BigDecimal` con escala fija y redondeo `HALF_EVEN`
  (redondeo bancario). **Nunca `double` ni `float`.** En base de datos, `NUMERIC(19,4)` para saldos
  y `NUMERIC(19,8)` para devengos.
- **Libro mayor de doble partida.** Cada movimiento genera asientos que suman cero. El saldo no es
  un campo mutable: es la suma de sus asientos. El dinero no puede aparecer ni desaparecer sin
  dejar rastro auditable.

**Tarjeta Ayni** — tarjeta de débito virtual.

- Emisión instantánea al aprobarse el KYC. PAN generado con algoritmo de Luhn sobre un BIN de
  pruebas.
- Controles del cliente: congelar y descongelar, límite diario de consumo, habilitar o deshabilitar
  compras por internet.
- El PAN completo se cifra en reposo y jamás se escribe en logs. La interfaz muestra únicamente los
  últimos cuatro dígitos, salvo una revelación puntual autenticada con MFA.
- **El CVV no se almacena**: se calcula y se muestra dinámicamente (principio central de PCI-DSS).

---

## 3. Arquitectura

### 3.1 Topología

Cinco servicios más la aplicación web. Cada servicio es hexagonal por dentro.

| Servicio | Lenguaje | Responsabilidad |
|---|---|---|
| `ayni-gateway` | Java · Spring Cloud Gateway | Enrutado, rate limiting, validación de JWT, CORS |
| `ayni-identity-service` | Java · Spring Boot | Registro, autenticación, MFA, perfil, orquestación del onboarding |
| `ayni-core-banking-service` | Java · Spring Boot | Cuentas, ledger, tarjetas, transferencias, devengo, outbox |
| `ayni-kyc-service` | Python · FastAPI | Detección de DNI, OCR, vivacidad, cotejo facial |
| `ayni-notification-service` | Java · Spring Boot | Correo y notificaciones; consume eventos |
| `ayni-web` | Next.js / React | Landing pública y banca en línea |

Infraestructura en contenedores: **PostgreSQL**, **MinIO** (S3), **RabbitMQ**, y para observabilidad
**Prometheus + Grafana + Loki**.

### 3.2 Decisión: microservicios por *bounded context*, no por entidad

**El dinero viaja junto.** Cuentas, asientos contables y transferencias comparten el mismo límite
transaccional dentro de `core-banking-service`. Separarlos convertiría una transferencia en una
*saga* distribuida con compensaciones, que es la principal fuente de dinero duplicado o perdido en
sistemas financieros reales.

Las **notificaciones sí se separan** porque no mueven dinero: si fallan se reintentan, y si se
duplican molestan pero no cuestan.

### 3.3 Estructura hexagonal (obligatoria en todos los servicios Java)

```
pe.ayni.bank.core
├── domain/                    ← CERO imports de Spring, JPA o HTTP
│   ├── model/                 Account, Money, Currency, LedgerEntry, Transfer
│   ├── service/               reglas de negocio puras
│   └── port/
│       ├── in/                OpenAccountUseCase, TransferMoneyUseCase
│       └── out/               AccountRepositoryPort, FxRatePort, EventPublisherPort
├── application/               orquestación, @Transactional, casos de uso
└── infrastructure/
    ├── in/web/                controladores REST, DTOs, mappers
    ├── out/persistence/       entidades JPA, repositorios, mappers
    ├── out/messaging/         publicador de outbox, RabbitMQ
    ├── out/fx/                cliente SUNAT/BCRP
    └── config/                beans, seguridad, resiliencia
```

**Regla verificada automáticamente:** el paquete `domain` no puede importar
`org.springframework`, `jakarta.persistence` ni `infrastructure`. Se comprueba con **ArchUnit** como
test en CI. Si se viola, el pipeline falla.

Consecuencias buscadas: el dominio se prueba con JUnit puro sin levantar Spring; sustituir
PostgreSQL o RabbitMQ toca un solo paquete. Esto materializa *modularidad*, *modificabilidad* y
*capacidad de prueba* de forma medible.

### 3.4 Comunicación entre servicios

| Ruta | Mecanismo | Justificación |
|---|---|---|
| web → gateway | REST/JSON sobre HTTPS | Punto de entrada único |
| gateway → servicios | REST interno | Enrutado y validación de JWT centralizados |
| identity → kyc | REST con **contrato OpenAPI**; cliente Java generado con `openapi-generator` | Si Python cambia el contrato, el build de Java falla |
| core-banking → notification | **Eventos por RabbitMQ vía outbox** | Desacoplado, sin pérdida, sin riesgo para el dinero |
| core-banking → SUNAT/BCRP | REST con Resilience4j y caché diaria | Ante caída, se usa el último tipo de cambio conocido |
| servicios → MinIO | SDK S3 con **URLs pre-firmadas** | Las imágenes no atraviesan la API |

Toda llamada saliente lleva **timeout, reintento con backoff exponencial y circuit breaker**
(Resilience4j).

### 3.5 Integración Java ↔ Python

1. El contrato del `kyc-service` se define en **OpenAPI 3**, versionado en `contracts/`.
2. Spring genera el cliente Java desde ese contrato en tiempo de build.
3. La llamada va envuelta en Resilience4j; ante fallo, el onboarding responde "verificación en
   revisión manual" en lugar de romperse.
4. **Las imágenes no viajan entre servicios.** El navegador sube a MinIO con URL pre-firmada; Java
   pasa a Python únicamente la clave del objeto; Python la lee de MinIO.
5. Se empieza síncrono con timeout de 10 s; se migra a asíncrono por RabbitMQ solo si las
   mediciones lo justifican.

### 3.6 Patrón Transactional Outbox

```
core-banking-service, UNA SOLA transacción ACID:
  1. debitar cuenta origen
  2. acreditar cuenta destino
  3. escribir asientos en el ledger
  4. INSERT en outbox_event          ← mismo COMMIT
  COMMIT
        ↓
  publicador lee outbox → publica en RabbitMQ → marca enviado
        ↓
  notification-service consume (idempotente por eventId) → envía correo
```

Garantías: si la transferencia hace rollback, no se notifica algo que no ocurrió; si RabbitMQ está
caído, el evento permanece en la tabla y se publica al restablecerse. La entrega es *at-least-once*,
por lo que el consumidor deduplica por `eventId`.

### 3.7 Multimoneda

- `Money` es un objeto de valor **inmutable**: `{ importe: BigDecimal, moneda: Currency }`. El
  dominio impide operar importes de monedas distintas.
- Cada cuenta y cada asiento llevan moneda. **Nunca se mezclan monedas en un mismo asiento.**
- Una transferencia entre monedas se descompone en tres asientos: salida en origen, conversión,
  entrada en destino, **registrando el tipo de cambio aplicado y su timestamp**. Sin ese registro no
  es posible auditar ni reconstruir el importe recibido.
- Compra y venta tienen tipos distintos: el banco gana en el spread, como en la realidad.

---

## 4. Datos

### 4.1 Almacenamiento de archivos

Los documentos de identidad y las selfies **no se guardan en la base de datos**. Hacerlo infla el
tamaño, arruina los backups, contamina la caché de PostgreSQL con datos que casi nunca se consultan
e impide servir la imagen sin pasarla por el backend.

Se usa **MinIO** (compatible con S3, open source, un contenedor). En PostgreSQL solo se guarda la
referencia: `object_key`, `hash_sha256`, `mime_type`, `tamaño`. El bucket es privado y el acceso se
hace con **URLs pre-firmadas de 5 minutos**. El hash SHA-256 permite detectar alteración del objeto.

Al hablar el protocolo de S3, migrar a AWS S3 real es cambiar la URL del endpoint.

### 4.2 Esquemas

Tres schemas en una misma instancia de PostgreSQL. **Ningún servicio lee tablas de otro.**

- **`identity`** — `usuario`, `persona`, `direccion`, `rol`, `permiso`, `usuario_rol`,
  `rol_permiso`, `solicitud_onboarding`, `documento_kyc`, `refresh_token`, `evento_auditoria`.
- **`core`** — `cliente` (proyección local), `producto`, `tasa_producto`, `cuenta`, `transaccion`,
  `asiento_contable`, `devengo_interes`, `tarjeta`, `control_tarjeta`, `transferencia`,
  `beneficiario`, `tipo_cambio`, `outbox_event`.
- **`notification`** — `plantilla`, `notificacion`, `intento_envio`.

Aproximadamente 25 tablas, normalizadas a 3FN.

### 4.3 Relaciones que cruzan contextos: proyección local

`core-banking-service` mantiene su propia tabla `cliente` con los datos mínimos que necesita (id,
nombres, documento enmascarado, estado), **sincronizada por eventos** desde `identity-service`. Así
`core.cuenta.cliente_id → core.cliente.id` es una clave foránea real, con integridad referencial
efectiva, y cada schema queda internamente completo y sin huérfanos.

Esto permite además separar las bases de datos en el futuro sin refactorizar, y aporta un caso de
estudio de consistencia eventual para el informe.

**Modelo lógico frente a modelo físico.** El EER que se entrega es el **modelo lógico**: un único
diagrama con las ~25 entidades y todas sus relaciones, incluidas las que cruzan contextos. El
**modelo físico** son los tres schemas con proyecciones locales. Ambos son correctos en su plano y
ambos se documentan.

### 4.4 Decisiones de modelado que requieren justificación

- **`tasa_producto` con vigencia, no un campo `tea` en `producto`.** Si la tasa cambia, los
  intereses ya devengados se calcularon con la tasa anterior y deben poder recalcularse y
  auditarse. Una tasa como campo simple destruye el histórico.
- **`asiento_contable.saldo_posterior` es desnormalización deliberada.** Rompe 3FN a propósito: sin
  ella, mostrar el saldo tras cada movimiento obliga a sumar todo el historial en cada consulta. Se
  documenta como decisión consciente de rendimiento, no como error de normalización.

### 4.5 Migraciones

Versionadas con **Flyway**, en el repositorio, revisadas por PR. **Nunca `ddl-auto: update`**: que
el ORM altere el esquema por su cuenta en producción es inaceptable en un sistema financiero.

---

## 5. Seguridad

### 5.1 Autenticación y sesión

- Contraseñas con **Argon2id**.
- **JWT de acceso de 15 minutos** más **refresh token rotativo** de 7 días en cookie
  `HttpOnly; Secure; SameSite=Strict`. La rotación implica que reutilizar un refresh delata el robo
  y provoca la invalidación de toda la familia de tokens.
- **MFA por TOTP** para operaciones sensibles: transferir, revelar PAN, cambiar contraseña.
- Bloqueo progresivo tras intentos fallidos y limitación de tasa en el gateway.

### 5.2 Cifrado

- En reposo, **AES-256-GCM** para PAN, número de documento y semilla TOTP. Claves fuera del código,
  inyectadas por entorno.
- En tránsito, TLS en todas las comunicaciones, incluidas las internas.
- MinIO con cifrado del lado del servidor.

### 5.3 Auditoría y trazabilidad

- `evento_auditoria` **append-only**: `UPDATE` y `DELETE` revocados a nivel de permisos de
  PostgreSQL. Registra actor, acción, recurso, IP, user-agent, resultado y timestamp.
- **Hibernate Envers** para versionado automático de entidades sensibles (`cuenta`, `tarjeta`,
  `persona`), permitiendo reconstruir el estado en cualquier momento pasado.
- Logs técnicos en **Loki + Grafana**, con prohibición absoluta de registrar PAN, contraseñas,
  tokens o datos de documento; enmascarado verificado en revisión de código.
- **Idempotencia obligatoria en transferencias** mediante cabecera `Idempotency-Key`. Un doble clic
  o un reintento de red no puede mover el dinero dos veces.

### 5.4 Segregación de funciones

En banca no existe un "superadministrador". Los permisos se reparten y nadie puede completar por sí
solo una operación sensible (**principio de cuatro ojos**: quien inicia no aprueba).

| Rol | Puede | No puede |
|---|---|---|
| `CLIENTE` | Operar sus propias cuentas | Ver cuentas ajenas |
| `OPERADOR` | Consultar clientes, iniciar bloqueos | Aprobar por sí solo, ver PAN completo |
| `SUPERVISOR` | Aprobar lo iniciado por el operador | Iniciar y aprobar la misma operación |
| `AUDITOR` | Leer todo, incluida la pista de auditoría | Escribir nada |
| `OFICIAL_CUMPLIMIENTO` | Revisar KYC, marcar operaciones sospechosas | Mover dinero |

### 5.5 Marcos de referencia

- **OWASP ASVS nivel 2** y **OWASP Top 10** como línea base verificable.
- **ISO/IEC 27001, Anexo A** para controles organizativos.
- **ISO/IEC 25010:2023** para calidad de producto.
- **Ley N.º 29733, Ley de Protección de Datos Personales del Perú**: el rostro es **dato sensible**
  y exige consentimiento explícito e informado. El onboarding incluye pantalla de consentimiento,
  política de retención y procedimiento de borrado.

### 5.6 Gestión de secretos

Ningún secreto en el repositorio. `.env` fuera de git, GitHub Secrets para CI/CD, y **Gitleaks en
cada push** para detectar filtraciones antes del merge.

---

## 6. Calidad y pruebas

### 6.1 Pirámide de pruebas

| Nivel | Herramienta | Qué prueba | Cuándo |
|---|---|---|---|
| Unitarias de dominio | JUnit 5 + AssertJ, sin Spring | Interés, partida doble, conversión, Luhn | Cada push |
| Arquitectura | **ArchUnit** | Que el dominio no importe Spring/JPA | Cada push |
| Integración | **Testcontainers** | Repositorios, Flyway, outbox | Cada PR |
| Contrato | OpenAPI + WireMock | Sincronía Java ↔ Python | Cada PR |
| Aceptación | **Cucumber** | Los escenarios DADO/CUANDO/ENTONCES de cada HU | Cada PR |
| End-to-end | **Playwright** | Onboarding, transferencia, congelar tarjeta | Tras desplegar a staging |
| Carga y estrés | **k6** | p95, punto de quiebre, concurrencia sobre el mismo saldo | Semanal y pre-release |
| Seguridad | Gitleaks, Trivy, OWASP Dependency-Check, ZAP | Secretos, CVEs, vulnerabilidades web | Cada PR; ZAP en staging |
| Mutación | **PIT** | Que los tests detecten fallos reales | Semanal |

Las **pruebas de mutación** se incluyen porque la cobertura miente: es posible tener 90 % de
cobertura con tests que no verifican nada. PIT altera el código a propósito y comprueba si algún
test falla.

### 6.2 Criterios de aceptación ejecutables

Los criterios de aceptación se escriben en el formato **DADO / CUANDO / ENTONCES** de la plantilla
oficial del curso (`Semana 01/HISTORIA DE USUARIO.docx`). Ese formato es Gherkin, de modo que cada
escenario se implementa directamente como test de Cucumber: **el criterio de aceptación es la
prueba automatizada**. Cada HU incluye además una lista breve de criterios en viñetas, conforme al
formato mostrado en las diapositivas.

### 6.3 ISO/IEC 25010:2023 — evidencia por característica

Se emplea la revisión de 2023 (nueve características).

| Característica | Evidencia |
|---|---|
| Adecuación funcional | Escenarios Gherkin ejecutados; matriz de trazabilidad HU → test |
| Eficiencia de desempeño | k6 con objetivo p95 < 500 ms; paneles de Grafana |
| Compatibilidad | Contrato OpenAPI verificado en CI; integración real con SUNAT |
| Capacidad de interacción | Lighthouse ≥ 90; WCAG 2.1 AA con axe-core en CI; pruebas con usuarios |
| Fiabilidad | Circuit breakers, health checks, restauración de respaldo cronometrada |
| Seguridad | OWASP ZAP, ASVS nivel 2, pista de auditoría, MFA |
| Mantenibilidad | ArchUnit, SonarQube, cobertura, pruebas de mutación |
| Flexibilidad | Imágenes multi-arquitectura, escalado por réplicas, doce factores |
| Protección | Validación de entrada, límites de operación, confirmaciones explícitas |

### 6.4 SLA y KPIs comprometidos

| Indicador | Objetivo |
|---|---|
| Disponibilidad mensual | 99.5 % |
| Latencia de API | p95 < 500 ms · p99 < 1 s |
| Onboarding KYC completo | < 90 s |
| Transferencia interna | < 3 s |
| Tasa de error | < 0.1 % |
| RPO / RTO | 24 h / 4 h |

Todos medidos con Prometheus y visibles en Grafana. Un SLA que no se mide no es un SLA.

---

## 7. Infraestructura y despliegue

### 7.1 Entornos

| Entorno | Dónde | Uso |
|---|---|---|
| Desarrollo | Máquina local, `docker compose` | Trabajo diario |
| Staging | **Raspberry Pi 5**, self-hosted runner | Integración y E2E automáticos |
| Producción | **Oracle Cloud Always Free** (4 vCPU ARM Ampere, 24 GB RAM, 200 GB) | Demo pública con dominio propio |

Ambos entornos son **ARM64**, de modo que la misma imagen Docker corre en los dos.

### 7.2 Adquisiciones y costos

| Recurso | Proveedor | Costo real |
|---|---|---|
| Producción (4 vCPU ARM, 24 GB RAM, 200 GB) | Oracle Cloud Always Free | S/ 0 permanente |
| Staging (servidor local) | Raspberry Pi 5, ya adquirida | S/ 0 · valor imputado ≈ S/ 450 |
| Dominio | Ya adquirido | ≈ S/ 45 / año |
| Repositorio y CI/CD (2000 min/mes) | GitHub Free | S/ 0 |
| Gestión del proyecto | Jira Free (≤ 10 usuarios) | S/ 0 |
| Análisis estático | SonarCloud (proyecto público) | S/ 0 |
| Túnel y TLS | Cloudflare Tunnel + Let's Encrypt | S/ 0 |
| Correo transaccional | Brevo / Resend, capa gratuita | S/ 0 |
| **Total recurrente** | | **≈ S/ 4 / mes** |

*Escenario de operación comercial* (referencia para el Acta): AWS con EC2 en dos zonas, RDS
Multi-AZ, S3, balanceador y CloudWatch ≈ **USD 300–450 / mes**. El contraste evidencia que la
elección de infraestructura es una decisión arquitectónica consciente.

### 7.3 GitFlow

```
main       ──●───────────────●──────────────●──   producción, protegida
              ╲             ╱ ╲            ╱
release        ╲   ●───●───●   ╲  ●───●───●       estabilización, SemVer
                ╲ ╱             ╲╱
develop    ──●───●───●───●───●───●───●───●────    integración continua
              ╲   ╱     ╲   ╱
feature        ●─●       ●─●                       una rama por Historia de Usuario
```

- Ramas: `feature/AYNI-42-registro-de-usuario`, `bugfix/AYNI-57-…`, `release/1.2.0`,
  `hotfix/1.2.1`.
- **Conventional Commits**: `feat(core-banking): calcular devengo diario de interés`, con
  `Refs: AYNI-42` en el cuerpo. Tipos: `feat` `fix` `docs` `style` `refactor` `perf` `test` `build`
  `ci` `chore` `revert`.
- `main` y `develop` protegidas: sin push directo, PR con una aprobación mínima y todos los checks
  en verde.
- `CHANGELOG.md` generado desde los commits.

### 7.4 Pipelines de CI/CD (GitHub Actions)

**En cada PR:** formato · Gitleaks · compilación · unitarias · ArchUnit · integración con
Testcontainers · umbral de cobertura JaCoCo · quality gate de SonarQube · Trivy · construcción de
imagen sin publicar.

**Al mergear a `develop`:** imagen multi-arquitectura `linux/arm64` publicada en GHCR · despliegue
automático a staging (Raspberry Pi 5) · pruebas de humo · suite Playwright · OWASP ZAP.

**Al mergear a `main`:** tag SemVer · **aprobación manual obligatoria** (GitHub Environment
`production`) · despliegue a Oracle Cloud · pruebas de humo post-despliegue · **rollback automático
a la imagen anterior si fallan**.

Cada despliegue a producción queda registrado con quién lo aprobó, qué commit contenía y qué
pruebas superó.

---

## 8. Organización del proyecto

### 8.1 Equipo y roles

| Integrante | Rol Scrum |
|---|---|
| Joaquín Alfonso Loa Denegri | **Product Owner** + Developer |
| Kiara Moshell Santti Saavedra | **Scrum Master** + Developer |
| Gerardo Raul Socualaya Mandamiento | Developer |
| Eduardo Vargas Zumaeta | Developer |

Los cuatro integrantes son Developers. El **Product Owner** define la visión, prioriza el Product
Backlog, formula el Product Goal y decide cuándo se despliega; no gestiona el cómo técnico. El
**Scrum Master** es un líder servicial: facilita los eventos de Scrum, retira impedimentos y vela
por el cumplimiento de la Definition of Done; no es jefe de proyecto ni secretario del equipo.

### 8.2 Catálogo de épicas

| Épica | Nombre | Bloque de valor |
|---|---|---|
| EP-00 | Iniciación y planificación | Acta, Gantt, backlog, matrices de riesgos e interesados |
| EP-01 | Fundación técnica y gobernanza | Repositorio, GitFlow, CI/CD, Docker, esqueleto hexagonal, design system |
| EP-02 | Identidad digital y onboarding KYC | Registro, autenticación, MFA, DNI, OCR, vivacidad, cotejo facial |
| EP-03 | Cuentas y núcleo transaccional | Apertura de cuenta, ledger de doble partida, saldos, devengo diario, TREA |
| EP-04 | Tarjeta de débito virtual | Emisión, controles del cliente, límites, congelar y descongelar |
| EP-05 | Transferencias e interoperabilidad | Transferencias internas, interbancarias, cámara de compensación simulada |
| EP-06 | Experiencia del cliente | Landing pública, panel, movimientos, estados de cuenta en PDF |
| EP-07 | Notificaciones y comunicaciones | Outbox, eventos, correo transaccional, plantillas |
| EP-08 | Multimoneda y tipo de cambio | Cuenta en dólares, conversión, integración real con SUNAT/BCRP |
| EP-09 | Operación y observabilidad | Back-office con segregación de funciones, métricas, logs, paneles, alertas |
| EP-10 | Calidad, endurecimiento y cierre | Pruebas de estrés, alta disponibilidad, recuperación, documentación final |

### 8.3 Calendario de sprints

Iniciación PMBOK del 14 al 17 de agosto; después **Sprint 0 de una semana más ocho sprints de dos
semanas**, hasta el 13 de diciembre de 2026.

| Sprint | Fechas | Meta | Épica |
|---|---|---|---|
| Iniciación | 14–17 ago | Acta, Gantt, backlog, riesgos, interesados | EP-00 |
| Sprint 0 · Fundación | 17–23 ago | Repo, CI/CD, Docker, esqueleto hexagonal, design system | EP-01 |
| Sprint 1 · Esqueleto Ambulante | 24 ago – 6 sep | Registro → login → cuenta abierta atravesando los 5 servicios | EP-02, EP-03 |
| Sprint 2 · Onboarding KYC | 7–20 sep | DNI, OCR, selfie, vivacidad, MinIO | EP-02 |
| Sprint 3 · Núcleo Transaccional | 21 sep – 4 oct | Cuentas, ledger de doble partida, devengo, TREA | EP-03 |
| Sprint 4 · Transferencias | 5–18 oct | Transferencias internas, outbox, notificaciones | EP-05, EP-07 |
| Sprint 5 · Tarjeta Débito | 19 oct – 1 nov | Emisión y controles de tarjeta | EP-04 |
| Sprint 6 · Interoperabilidad | 2–15 nov | Interbancarias, multimoneda, SUNAT | EP-05, EP-08 |
| Sprint 7 · Experiencia | 16–29 nov | Estados de cuenta, back-office, accesibilidad, observabilidad | EP-06, EP-09 |
| Sprint 8 · Endurecimiento | 30 nov – 13 dic | Estrés, alta disponibilidad, recuperación, documentación | EP-10 |

Los Sprint Review de los sprints 1, 3, 5 y 8 caen inmediatamente antes de cada evaluación del curso
(semanas 5, 9, 13 y 18), de modo que cada entrega se presenta con un incremento recién cerrado.

### 8.4 Decisión: el Sprint 1 es un "esqueleto ambulante"

En lugar de completar un servicio primero, el Sprint 1 entrega un flujo fino que **atraviesa la
arquitectura entera**: navegador → gateway → identity-service → kyc-service (que de momento aprueba
siempre) → evento → core-banking-service crea la cuenta → notification-service envía el correo.

Funcionalmente es pobre; arquitectónicamente lo valida todo. El riesgo principal de una arquitectura
distribuida es descubrir tarde que los servicios no se entienden. Con el esqueleto ambulante ese
riesgo se materializa —o se descarta— en la semana 4. El Sprint 2 sustituye el KYC simulado por el
real sin tocar la integración.

### 8.5 Gestión en Jira

Proyecto `AYNI` en `jloadenegri.atlassian.net`, plantilla Scrum. Jerarquía `Epic → Story → Subtask`,
más `Bug` y `Spike`. Estimación en puntos de historia (Fibonacci).

Cada Story lleva su clave, que se propaga a la rama, al commit y al PR, produciendo trazabilidad de
punta a punta: requisito → tarea → rama → commit → PR → despliegue.

El **Gantt se mantiene además en Mermaid** (`docs/gestion/gantt.mmd`) dentro del repositorio, para
que el diagrama entregable sea versionable, reproducible e independiente de que la vista Cronograma
de Jira renderice correctamente.

---

## 9. Repositorio

### 9.1 Estructura (monorepo)

```
ayni-bank/
├── .github/workflows/        pipelines de CI/CD
├── services/
│   ├── ayni-gateway/
│   ├── ayni-identity-service/
│   ├── ayni-core-banking-service/
│   ├── ayni-notification-service/
│   └── ayni-kyc-service/          ← Python
├── web/ayni-web/                  ← Next.js
├── contracts/                     ← OpenAPI, fuente de verdad de las APIs
├── infra/
│   ├── docker/                    compose de desarrollo, staging y producción
│   └── observability/             Prometheus, Grafana, Loki
├── docs/
│   ├── gestion/                   Acta, Gantt, backlog, riesgos, interesados
│   ├── arquitectura/              C4, EER, secuencias, ADRs
│   ├── calidad/                   SLA, plan de pruebas, ISO 25010
│   └── marca/                     logo SVG, paleta, tipografía, assets
└── brand/                         archivos fuente de diseño
```

Se elige monorepo sobre cinco repositorios porque, con cuatro personas, un cambio que cruza
servicios exigiría cinco PRs coordinados. Los pipelines se filtran por ruta, de modo que solo se
reconstruye lo que cambió.

### 9.2 Documentos guía

`README.md` · `CONTRIBUTING.md` · `CODESTYLE.md` · `SECURITY.md` · `ROADMAP.md` ·
`CHANGELOG.md` · `DEFINITION_OF_DONE.md` · `docs/arquitectura/adr/` (un archivo por decisión, con
contexto, opciones y consecuencias).

**Este documento (`docs/arquitectura/diseno-base.md`) es la especificación viva del proyecto.** Se
descartaron `SPECS.md` y `ARCHITECTURE.md` como archivos separados para evitar tres documentos que
describen lo mismo y se contradicen entre sí con el tiempo. Una sola fuente de verdad.

`.gitignore` excluye archivos de entorno (`.env`, `*.local.*`), artefactos de construcción,
directorios de configuración local de editores y herramientas, y cualquier archivo de credenciales.

### 9.3 Diagramas a producir

Todos en **Mermaid o PlantUML dentro del repositorio**, versionados y diffeables; nunca capturas
sueltas.

C4 nivel 1 (contexto) · C4 nivel 2 (contenedores) · C4 nivel 3 (componentes de core-banking) ·
**EER lógico global** · modelo físico por contexto · BPMN AS-IS y TO-BE · secuencia de onboarding
KYC · secuencia de transferencia con outbox · máquinas de estados (solicitud, transferencia,
tarjeta) · diagrama de despliegue.

---

## 10. Identidad visual

- **Logo en SVG escrito a mano**: dos formas entrelazadas que representan la reciprocidad del
  *ayni*. Vectorial, versionado, adaptable a tema claro y oscuro.
- **Paleta y tipografía como design tokens** en variables CSS, consumidos por Tailwind. Una sola
  fuente de verdad para el sitio y los mockups.
- **Mockups y wireframes** generados con el MCP de Stitch a partir del design system.
- **Imágenes de la landing**: se generan con IA cuando el banco de imágenes libre no ofrezca lo
  necesario, que es el caso previsto. `docs/marca/SOLICITUDES-ASSETS.md` lista, por sprint, cada
  imagen requerida con su prompt, dimensiones, formato y ubicación de uso.
  `docs/marca/ASSETS.md` registra la procedencia y licencia de todos los assets.

---

## 11. Entregables de la fase de iniciación (antes del 17 de agosto)

1. Acta de Constitución del Proyecto, sobre la plantilla del Institute of Project Management.
2. Diagrama de Gantt híbrido en bloques de valor y épicas.
3. Doce Historias de Usuario (nueve funcionales, tres no funcionales).
4. Criterios de aceptación en formato DADO/CUANDO/ENTONCES para cinco de ellas.
5. Product Backlog priorizado.
6. Matriz de gestión de interesados.
7. Matriz de gestión de riesgos, con probabilidades sustentadas en datos reales del Perú.
8. Listado de adquisiciones con costos estimados.
9. Línea base del alcance de alto nivel.

---

## 12. Restricciones y supuestos

### Restricciones

- Plazo fijo e improrrogable: 13 de diciembre de 2026.
- Equipo de cuatro estudiantes con carga académica adicional.
- Presupuesto efectivamente nulo.
- Fechas de evaluación externas fijadas en las semanas 5, 9, 13 y 18.
- Sin acceso a APIs reales de RENIEC ni de la cámara de compensación interbancaria.

### Supuestos

- Semana 1 del ciclo: 10–16 de agosto de 2026; semana 18: 7–13 de diciembre de 2026.
- Oracle Cloud Always Free mantiene su disponibilidad durante el ciclo.
- La API de tipo de cambio de SUNAT/BCRP permanece accesible públicamente.
- Los cuatro integrantes dominan Java y Spring Boot.

### Simuladores con contrato definido

Se implementan como servicios simulados, con su contrato OpenAPI documentado y declarados
explícitamente como tales en el informe: **RENIEC** (validación de identidad) y **cámara de
compensación interbancaria** (transferencias hacia otros bancos). El tipo de cambio de SUNAT/BCRP
**sí es una integración real**.
