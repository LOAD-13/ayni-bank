# Sprint Backlog — Sprint 4 · Transferencias y Notificaciones

**5 – 18 de octubre de 2026** · 2 semanas · 2 ítems · 21 puntos

## Sprint Goal

> Que el dinero se mueva entre cuentas Ayni de forma atómica e idempotente, y que el cliente reciba
> aviso de cada movimiento sin que el sistema de notificaciones pueda comprometer una transacción.

## Capacidad

120 h disponibles · Comprometido: 102 h

---

### `AYNI-18` · HU-07 · Transferencia entre cuentas Ayni [13 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Modelar `Transferencia` y su máquina de estados en el dominio | 4h |
| 2 | Implementar el caso de uso con transacción única y bloqueo pesimista | 5h |
| 3 | Implementar la idempotencia mediante cabecera `Idempotency-Key` | 5h |
| 4 | Migración Flyway de `transferencia` y `beneficiario` | 2h |
| 5 | Validar saldo suficiente y estado de ambas cuentas antes de mover | 3h |
| 6 | Escribir el evento en `outbox_event` dentro del mismo COMMIT | 3h |
| 7 | Exigir confirmación con segundo factor TOTP | 3h |
| 8 | Generar comprobante con número único y descarga en PDF | 4h |
| 9 | **Pruebas de concurrencia:** dos transferencias simultáneas sobre el mismo saldo | 5h |
| 10 | Pantalla de transferencia con confirmación en dos pasos | 6h |
| 11 | Feature de Cucumber con los cinco escenarios | 4h |

### `AYNI-24` · HU-13 · Notificación por correo de cada movimiento [8 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Implementar el publicador de outbox con marcado de enviados | 4h |
| 2 | Configurar exchanges y colas en RabbitMQ | 3h |
| 3 | Implementar el consumidor idempotente por `eventId` | 4h |
| 4 | Migración Flyway de `plantilla`, `notificacion` e `intento_envio` | 2h |
| 5 | Diseñar las plantillas de correo con la identidad de marca | 4h |
| 6 | Integrar el proveedor de correo transaccional | 3h |
| 7 | Implementar reintentos con backoff exponencial | 3h |
| 8 | Verificar que ningún dato sensible aparece en el correo ni en los logs | 2h |
| 9 | Alerta si hay eventos pendientes en outbox con más de 5 minutos | 2h |
| 10 | Feature de Cucumber con los tres escenarios | 3h |

---

## Riesgos del sprint

| Riesgo | Mitigación |
|---|---|
| Pérdida de integridad contable por concurrencia (R-05) | Bloqueo pesimista, idempotencia y pruebas de concurrencia automatizadas como subtarea explícita |
| El outbox introduce latencia perceptible | Se mide; si supera los 5 s se ajusta el intervalo del publicador |

## Definition of Done

Aplica la de [`DEFINITION_OF_DONE.md`](../../../DEFINITION_OF_DONE.md).

**Adicional:** la prueba de concurrencia debe demostrar que **en ningún caso el saldo queda
negativo** ni se duplica un movimiento. Sin esa evidencia, la historia no está terminada.
