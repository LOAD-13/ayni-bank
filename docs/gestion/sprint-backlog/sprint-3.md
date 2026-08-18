# Sprint Backlog — Sprint 3 · Núcleo Transaccional

**21 de septiembre – 4 de octubre de 2026** · 2 semanas · 2 ítems · 18 puntos

## Sprint Goal

> Que el saldo del cliente genere intereses todos los días sobre un libro mayor de doble partida
> auditable, y que pueda consultar sus movimientos.

**Hito asociado:** despliegue a producción versión 1.

## Capacidad

80 h disponibles · Comprometido: 64 h. Se reserva más holgura de lo habitual porque este sprint
cierra con el primer despliegue a producción.

---

### `AYNI-17` · HU-06 · Rendimiento diario del saldo con TREA [13 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Modelar `LibroMayor` y `AsientoContable` con partida doble en el dominio | 5h |
| 2 | Implementar la invariante de que los asientos de una transacción suman cero | 3h |
| 3 | Migración Flyway de `transaccion`, `asiento_contable` y `devengo_interes` | 3h |
| 4 | Implementar el cálculo de devengo diario con año comercial de 360 días | 5h |
| 5 | Asegurar precisión con `BigDecimal` y redondeo `HALF_EVEN` en todo el flujo | 3h |
| 6 | Implementar la capitalización mensual el último día del mes | 4h |
| 7 | Implementar la gestión de tasas con vigencia histórica | 4h |
| 8 | Programar la ejecución diaria del proceso de devengo | 3h |
| 9 | Mostrar TREA y rendimiento acumulado en la interfaz | 3h |
| 10 | Pruebas de precisión: año bisiesto, saldo cero, cambio de tasa | 4h |
| 11 | Feature de Cucumber con los cuatro escenarios | 3h |

### `AYNI-19` · HU-08 · Consulta de saldo e historial de movimientos [5 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Implementar la consulta de saldo derivado de los asientos | 3h |
| 2 | Añadir `saldo_posterior` como desnormalización deliberada y documentada | 2h |
| 3 | Endpoint paginado de movimientos con filtros por fecha, tipo e importe | 4h |
| 4 | Verificar autorización por recurso para impedir acceso a cuentas ajenas | 3h |
| 5 | Registrar los intentos de acceso indebido en la auditoría | 2h |
| 6 | Panel principal con saldo, rendimiento del mes y TREA vigente | 5h |
| 7 | Listado de movimientos con filtros y paginación | 5h |
| 8 | Feature de Cucumber con los dos escenarios | 2h |

---

## Riesgos del sprint

| Riesgo | Mitigación |
|---|---|
| Error de precisión en el cálculo de interés (R-05) | Prohibición absoluta de `double`; pruebas con valores conocidos calculados a mano |
| Primer despliegue a producción, con infraestructura nueva | Provisionar Oracle Cloud en la primera semana del sprint, no en la última |

## Definition of Done

Aplica la de [`DEFINITION_OF_DONE.md`](../../../DEFINITION_OF_DONE.md), incluida la sección de
despliegue a producción: respaldo previo verificado, aprobación del Product Owner y pruebas de humo
posteriores.
