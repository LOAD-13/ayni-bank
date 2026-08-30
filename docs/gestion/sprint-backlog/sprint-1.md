# Sprint Backlog — Sprint 1 · Esqueleto Ambulante

**24 de agosto – 6 de septiembre de 2026** · 2 semanas · 6 ítems · 45 puntos

## Sprint Goal

> Que un visitante se registre, inicie sesión con segundo factor y obtenga una cuenta de ahorro
> abierta, **atravesando los cinco servicios de punta a punta**.

## Por qué este sprint es así

Funcionalmente entrega poco. Arquitectónicamente lo valida todo: navegador → gateway →
`identity-service` → `kyc-service` (que de momento aprueba siempre) → evento por RabbitMQ →
`core-banking-service` crea la cuenta → `notification-service` envía el correo.

El riesgo principal de una arquitectura distribuida es **descubrir tarde que los servicios no se
entienden**. Con el esqueleto ambulante ese riesgo se materializa —o se descarta— en la cuarta
semana del proyecto, cuando aún queda el 75% del tiempo para reaccionar. El Sprint 2 sustituye el
KYC simulado por el real sin tocar la integración.

## Capacidad

120 h disponibles · Comprometido: **138 h** · **Holgura negativa: −15 %**

**El sprint está sobrecomprometido a conciencia, y dos veces.**

`AYNI-119` entró después de la planificación porque los mockups son el entregable evaluado de la
sesión 4 y no admitían aplazamiento. Se decidió añadirlo sin retirar ningún ítem.

`AYNI-120` entró más tarde todavía, al empezar HU-01: el Sprint 0 dejó `web/ayni-web/` vacío y
`contracts/` sin contenido, de modo que la subtarea `AYNI-75` —el formulario de registro— no tenía
dónde vivir. No es alcance nuevo, es alcance que se descubrió pendiente. Que aparezca ahora y no en
la planificación es material de retrospectiva: **el Sprint 0 se dio por cerrado con un habilitador
sin hacer**, y nadie lo detectó hasta tropezar con él.

El candidato natural a moverse era `AYNI-31` (13 pts), pero es el que monta staging y la Definition
of Done de este sprint exige demostrar el flujo **sobre staging, no en local**. Sacarlo obligaba a
relajar esa condición.

Queda registrado para la retrospectiva: si el sprint no cierra completo, la causa es esta y no una
mala estimación.

---

## Ítems y plan técnico

### `AYNI-120` · T-11 · Andamiaje de la aplicación web y contrato de identity [3 pts]

Habilitador de `AYNI-12`. No entrega valor observable por el cliente: entrega el sitio donde
`AYNI-75` puede existir.

| # | Subtarea | Est. |
|---|---|---|
| 1 | Andamiar `ayni-web` con Next.js 15, React 19, TypeScript y Tailwind 4 | 2h |
| 2 | Traducir los tokens de marca a `src/styles/tokens.css` y exponerlos a Tailwind | 2h |
| 3 | Implementar los seis scripts que exige el job `web` del pipeline | 3h |
| 4 | Dockerfile ARM64 sobre la salida `standalone` y reincorporación al compose | 3h |
| 5 | Contrato OpenAPI de `POST /api/v1/registro` con errores RFC 7807 | 2h |

**Ningún componente escribe un hexadecimal.** La paleta vive en un solo fichero, derivado de
`docs/marca/design-tokens.md`, y se consume como utilidades de Tailwind.

### `AYNI-119` · T-10 · Prototipo interactivo y biblioteca de componentes [8 pts]

Entregable evaluado de la sesión 4. El docente lo pidió de forma explícita: los mockups son
obligatorios y, como mínimo, debe estar el flujo de registro con verificación facial, que **no es
una foto estática sino una prueba de vivacidad en movimiento**.

| # | Subtarea | Est. |
|---|---|---|
| 1 | Cargar los tokens de marca como variables del documento | 1h |
| 2 | Construir los átomos: botones, campos, badges, indicador de progreso | 3h |
| 3 | Landing pública con la TREA publicada y el simulador de rendimiento | 4h |
| 4 | Flujo de registro: datos, DNI anverso, DNI reverso con OCR | 4h |
| 5 | Prueba de vivacidad como secuencia de cuatro estados | 3h |
| 6 | Resultado de la verificación en sus dos variantes: aprobada y en revisión | 2h |
| 7 | Inicio de sesión con segundo factor TOTP | 3h |
| 8 | Panel de banca por internet con la tarjeta de débito reutilizable | 4h |

Herramienta: **pen.dev**, decidida en [ADR-0007](../../arquitectura/adr/0007-pen-dev-para-prototipado-de-interfaces.md).
El contexto que se entrega al agente vive en `ayni-bank-workspace-design/contexto/`, fuera del
repositorio.

### `AYNI-31` · HU-20 · [NF] Despliegue continuo con aprobación [13 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Registrar el self-hosted runner de GitHub Actions en la Raspberry Pi 5 | 3h |
| 2 | Crear el workflow de publicación de imágenes ARM64 en GHCR | 3h |
| 3 | Escribir `docker-compose.staging.yml` | 2h |
| 4 | Automatizar el despliegue a staging al fusionar a `develop` | 3h |
| 5 | Escribir los scripts de espera de salud y pruebas de humo | 3h |
| 6 | Configurar Cloudflare Tunnel y TLS para el dominio de staging | 3h |
| 7 | Configurar el entorno `production` con revisor obligatorio | 1h |
| 8 | Implementar el rollback automático ante fallo de pruebas de humo | 3h |

### `AYNI-12` · HU-01 · Registro de usuario [5 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Modelar `Usuario` y la política de contraseñas en el dominio, sin framework | 2h |
| 2 | Implementar el adaptador de hashing con Argon2id | 2h |
| 3 | Migración Flyway de las tablas `usuario` y `persona` | 2h |
| 4 | Adaptador de persistencia JPA con su mapper de dominio | 3h |
| 5 | Endpoint `POST /api/v1/registro` con validación y respuesta RFC 7807 | 3h |
| 6 | Prevención de enumeración de usuarios en la respuesta de error | 1h |
| 7 | Feature de Cucumber con los cuatro escenarios de aceptación | 3h |
| 8 | Formulario de registro en Next.js con validación en cliente | 4h |

### `AYNI-15` · HU-04 · Inicio de sesión seguro con segundo factor [8 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Configurar Spring Security con emisión de JWT de 15 minutos | 3h |
| 2 | Implementar refresh token rotativo con detección de reutilización | 4h |
| 3 | Migración Flyway de la tabla `refresh_token` | 1h |
| 4 | Implementar MFA por TOTP: generación de semilla y verificación | 4h |
| 5 | Cifrado AES-256-GCM de la semilla TOTP en reposo | 2h |
| 6 | Bloqueo progresivo tras intentos fallidos | 2h |
| 7 | Registrar todo acceso en la pista de auditoría | 2h |
| 8 | Validación de JWT en el gateway | 2h |
| 9 | Feature de Cucumber con los cuatro escenarios | 3h |
| 10 | Pantalla de inicio de sesión y de verificación TOTP | 4h |

### `AYNI-16` · HU-05 · Apertura automática de cuenta de ahorro [8 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Modelar `CuentaAhorro`, `Money` y `Moneda` en el dominio | 3h |
| 2 | Implementar la generación de número de cuenta y CCI de 20 dígitos | 3h |
| 3 | Migración Flyway de `cuenta`, `producto`, `tasa_producto` y `cliente` | 2h |
| 4 | Implementar la tabla `outbox_event` y su publicador | 4h |
| 5 | Emitir el evento `CuentaAperturada` dentro de la misma transacción | 2h |
| 6 | Consumir en `core-banking` el evento de KYC aprobado desde `identity` | 3h |
| 7 | Implementar la proyección local de `cliente` sincronizada por eventos | 3h |
| 8 | Impedir cuentas duplicadas por cliente y moneda | 2h |
| 9 | Feature de Cucumber con los cuatro escenarios | 3h |

---

## Riesgos del sprint

| Riesgo | Mitigación |
|---|---|
| El self-hosted runner en la Pi da problemas de red o permisos | Empezar por él el primer día; hay dos semanas de margen |
| La integración por eventos entre `identity` y `core` resulta más compleja de lo previsto | Es justo el objetivo del sprint: descubrirlo ahora |
| El equipo no domina Spring Security | Trabajo por pares en `AYNI-15`, que es el ítem más delicado |

## Definition of Done

Aplica la de [`DEFINITION_OF_DONE.md`](../../../DEFINITION_OF_DONE.md).

**Adicional para este sprint:** el flujo completo debe demostrarse en vivo sobre **staging**, no en
local. Un esqueleto ambulante que solo camina en la máquina de un desarrollador no prueba nada.

---

## Cierre del sprint · 30 de agosto de 2026

### Qué se entregó

| Ítem | Pts | Estado |
|---|---|---|
| `AYNI-120` · Andamiaje web y contratos | 3 | ✅ |
| `AYNI-119` · Mockups en pen.dev | 5 | ✅ |
| `AYNI-12` · HU-01 · Registro de usuario | 5 | ✅ |
| `AYNI-15` · HU-04 · Inicio de sesión con segundo factor | 8 | ✅ |
| `AYNI-16` · HU-05 · Apertura de cuenta (esqueleto) | 8 | ✅ |
| `AYNI-31` · HU-20 · Despliegue continuo | 13 | ➡️ movido al Sprint 2 |

**29 de 42 puntos entregados.** Los 13 restantes se movieron, no se perdieron.

### El objetivo del sprint se cumplió

El recorrido completo está verificado contra la infraestructura real, no simulado:

```
registro por el gateway ....... 202
aprobación (perfil dev) ....... 202  → solicitud APROBADA, usuario ACTIVO
evento por RabbitMQ ........... CuentaAperturada, publicado desde la bandeja de salida
cuenta abierta ................ ACTIVA PEN · CCI 999-001-0110-0000-0004-14 · saldo 0.00
idempotencia .................. reenviar el mismo evento no abre una segunda cuenta
```

Cuatro servicios encadenados —web, gateway, identity, core-banking— más PostgreSQL y RabbitMQ. El
riesgo que este sprint existía para descartar queda descartado: **los servicios se entienden**.

### Por qué se movió `AYNI-31`

La Definition of Done exigía demostrar el flujo sobre staging. Staging no existe porque requiere
registrar un *self-hosted runner* en la Raspberry Pi, que es trabajo de máquina y no de código: sin
él, GitHub Actions no tiene dónde ejecutarse, porque la Pi está detrás de un router doméstico sin IP
pública.

**Mover el ítem no compromete el objetivo del sprint**, que es validar la integración, y eso se
demuestra sobre `docker compose`. Lo que sí obliga es a relajar la condición de «sobre staging» para
esta revisión concreta. Decisión del PO, tomada el 30 de agosto.

Se recomienda **partirlo en dos** al replanificarlo: el workflow (código, sin dependencias) y el
registro del runner (hardware). Así los 13 puntos dejan de estar bloqueados en bloque.

### Alcance añadido durante el sprint

Tres cosas que no estaban planificadas y entraron por decisión del PO:

1. **Identidad declarada en el registro** (ADR-0009). Los datos que el formulario aprobado ya pedía
   dejaron de descartarse: se guardan cifrados como término de comparación del OCR de HU-02. Incluye
   la comprobación de mayoría de edad.
2. **Alta del segundo factor.** HU-04 daba por hecho que el MFA estaba configurado, pero ninguna
   historia lo configuraba. Sin ello, el escenario 1 era inalcanzable. Ver ADR-0010.
3. **Pantalla final del onboarding.** Es el resultado visible de HU-05: si la cuenta se abre y nadie
   la ve, el esqueleto ambulante no se puede demostrar en la revisión.

### Deuda que se lleva el Sprint 2

Catalogada en [`pendientes-sprint-2.md`](../pendientes-sprint-2.md). Lo que más pesa:

- El disparador de aprobación bajo perfil `dev` sustituye al OCR y hay que retirarlo con HU-02.
- `identity` publica sin bandeja de salida; `core-banking` sí la usa. Se cierra con HU-13.
- Los endpoints de consulta toman el titular de la ruta y no del token. Se cierra con HU-07.
- El botón «Entrar con la biometría del dispositivo» está en el prototipo y **no tiene historia**.

### Para la retrospectiva

- **Sobrecompromiso al 138 %.** Se sabía de antemano y se registró. El resultado —29 de 42— confirma
  que la capacidad era la que era. Conviene no repetirlo sin retirar algo a cambio.
- **Dos huecos de planificación** aparecieron construyendo, no planificando: el andamiaje web que el
  Sprint 0 dio por cerrado, y el alta del segundo factor que HU-04 daba por hecha. Ambos se
  detectaron al tropezar con ellos. Merece la pena revisar las HUs buscando premisas no cubiertas
  antes de comprometerlas.
- **El prototipo y las historias discrepan en varios puntos** (intentos de bloqueo, reenvío de
  código). Manda Jira, pero el prototipo hay que corregirlo o quedará documentando algo que no es.
- **`mvn test` en local puede pasar mientras el build limpio falla.** Costó una tarde. Para dar algo
  por bueno: `./mvnw clean verify`.
