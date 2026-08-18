# Sprint Backlog — Sprint 6 · Interoperabilidad y Multimoneda

**2 – 15 de noviembre de 2026** · 2 semanas · 2 ítems · 26 puntos

## Sprint Goal

> Que el dinero salga hacia otros bancos por CCI y que el cliente pueda ahorrar en dólares con
> tipo de cambio obtenido de una fuente externa **real**.

Es el sprint que demuestra **compatibilidad e interoperabilidad** según ISO/IEC 25010.

## Capacidad

80 h disponibles · Comprometido: 70 h

---

### `AYNI-22` · HU-11 · Transferencia interbancaria mediante CCI [13 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Implementar la validación del CCI de 20 dígitos con su dígito de control | 3h |
| 2 | Definir el contrato OpenAPI de la cámara de compensación | 4h |
| 3 | Implementar el simulador de cámara de compensación conforme al contrato | 6h |
| 4 | Implementar el adaptador de salida con Resilience4j | 4h |
| 5 | Extender la máquina de estados: PENDIENTE, COMPLETADA, RECHAZADA | 4h |
| 6 | Implementar los asientos de compensación para la reversión | 5h |
| 7 | Implementar el tiempo máximo de espera y su reversión automática | 4h |
| 8 | Gestión de beneficiarios frecuentes | 4h |
| 9 | Pantalla de transferencia interbancaria con validación de CCI en vivo | 5h |
| 10 | Feature de Cucumber con los tres escenarios | 3h |

### `AYNI-23` · HU-12 · Cuenta en dólares y tipo de cambio real [13 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Extender `Money` para impedir operar entre monedas distintas | 3h |
| 2 | Migración Flyway de `tipo_cambio` y ajuste multimoneda del ledger | 3h |
| 3 | Implementar el adaptador **real** de SUNAT/BCRP | 5h |
| 4 | Implementar la caché diaria del tipo de cambio | 3h |
| 5 | Circuit breaker con respaldo al último valor conocido | 3h |
| 6 | Advertir al cliente cuando se usa un tipo de cambio en caché | 2h |
| 7 | Implementar el spread de compra y venta | 3h |
| 8 | Implementar la conversión en tres asientos sin mezclar monedas | 6h |
| 9 | Registrar de forma inmutable el tipo aplicado y su timestamp | 3h |
| 10 | Apertura de cuenta en dólares | 3h |
| 11 | Pantalla de conversión con simulación previa | 5h |
| 12 | Feature de Cucumber con los tres escenarios | 3h |

---

## Riesgos del sprint

| Riesgo | Mitigación |
|---|---|
| El servicio de SUNAT/BCRP no responde o cambia (R-12) | Caché diaria, circuit breaker y último valor conocido con advertencia explícita |
| Las transferencias interbancarias introducen estados intermedios y reversión | La cámara es un simulador con contrato: se controla el escenario de rechazo a voluntad |

## Definition of Done

Aplica la de [`DEFINITION_OF_DONE.md`](../../../DEFINITION_OF_DONE.md).

**Adicional:** debe poder **auditarse una conversión pasada** y recalcularse el importe recibido a
partir del tipo de cambio registrado. Si no se puede reconstruir, no está terminado.
