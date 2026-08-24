# Sprint Backlog — Sprint 5 · Tarjeta de Débito Virtual

**19 de octubre – 1 de noviembre de 2026** · 2 semanas · 2 ítems · 13 puntos

## Sprint Goal

> Que el cliente disponga de una tarjeta de débito virtual desde la apertura de su cuenta y pueda
> controlarla íntegramente desde la web.

**Hito asociado:** despliegue a producción versión 2.

## Capacidad

120 h disponibles · Comprometido: 78 h. Sprint deliberadamente holgado: absorbe la deuda técnica
acumulada de los sprints 2 a 4 y prepara el despliegue v2.

---

### `AYNI-20` · HU-09 · Emisión de tarjeta de débito virtual [8 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Modelar `Tarjeta` y su máquina de estados en el dominio | 3h |
| 2 | Implementar la generación de PAN con algoritmo de Luhn sobre BIN de pruebas | 4h |
| 3 | Garantizar unicidad del PAN generado | 2h |
| 4 | Implementar el cifrado AES-256-GCM del PAN en reposo | 4h |
| 5 | Migración Flyway de `tarjeta` y `control_tarjeta` | 2h |
| 6 | Implementar el CVV dinámico, **sin almacenarlo nunca** | 4h |
| 7 | Emitir la tarjeta automáticamente al aprobarse el KYC | 3h |
| 8 | Enmascarar el PAN en interfaz, logs y respuestas de API | 3h |
| 9 | Revelación puntual del PAN con MFA y registro en auditoría | 4h |
| 10 | Componente visual de tarjeta con la identidad de marca | 5h |
| 11 | Feature de Cucumber con los dos escenarios | 2h |

### `AYNI-21` · HU-10 · Control de la tarjeta [5 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Implementar congelar y descongelar con efecto inmediato | 3h |
| 2 | Implementar el límite diario de consumo y su acumulado | 4h |
| 3 | Implementar la habilitación de compras por internet | 2h |
| 4 | Rechazar todo consumo sobre tarjeta congelada | 2h |
| 5 | Registrar cada cambio de configuración en la auditoría | 2h |
| 6 | Notificar al cliente cada cambio en los controles | 2h |
| 7 | Panel de controles de tarjeta en la interfaz | 5h |
| 8 | Feature de Cucumber con los dos escenarios | 2h |

---

## Definition of Done

Aplica la de [`DEFINITION_OF_DONE.md`](../../../DEFINITION_OF_DONE.md), incluida la sección de
despliegue a producción.

**Adicional:** verificación explícita de que **el PAN completo no aparece en ningún log** y de que
**el CVV no está en base de datos**. Es el principio central de PCI-DSS y se comprueba, no se
supone.
