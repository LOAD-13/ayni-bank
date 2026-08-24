# Sprint Backlog — Sprint 1 · Esqueleto Ambulante

**24 de agosto – 6 de septiembre de 2026** · 2 semanas · 4 ítems · 34 puntos

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

120 h disponibles · Comprometido: 102 h · Holgura: 15%

---

## Ítems y plan técnico

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
