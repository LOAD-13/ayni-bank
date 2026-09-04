# Sprint Backlog — Sprint 3 · Núcleo Transaccional

**21 de septiembre – 4 de octubre de 2026** · 2 semanas · 3 ítems · 23 puntos

## Sprint Goal

> Que el saldo del cliente genere intereses todos los días sobre un libro mayor de doble partida
> auditable, y que pueda consultar sus movimientos.

**Hito asociado:** despliegue a producción versión 1.

## Capacidad

120 h disponibles · Comprometido: 115 h (96 h del núcleo transaccional + 19 h de `AYNI-123`). La
holgura que este sprint reservaba para el primer despliegue a producción se consume en parte con la
incorporación de la recuperación de contraseña.

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

### `AYNI-123` · HU-21 · Recuperación de la contraseña [5 pts]

| # | Subtarea | Clave | Est. |
|---|---|---|---|
| 1 | Migración Flyway de la tabla `token_recuperacion` | `AYNI-137` | 1h |
| 2 | Emisión de token de un solo uso con caducidad de 30 minutos | `AYNI-138` | 2h |
| 3 | Endpoint `POST /api/v1/recuperacion` con respuesta indistinguible | `AYNI-139` | 2h |
| 4 | Endpoint de confirmación y cambio de contraseña con la política vigente | `AYNI-140` | 3h |
| 5 | Invalidar sesiones y familias de refresh al cambiar la contraseña | `AYNI-141` | 2h |
| 6 | Correo de recuperación en `notification-service` | `AYNI-142` | 2h |
| 7 | Las tres pantallas de recuperación, con el estado de enlace caducado | `AYNI-143` | 4h |
| 8 | Feature de Cucumber con los cinco escenarios | `AYNI-144` | 3h |

**Dos reglas que no se negocian en esta historia.** La respuesta a la petición de recuperación es
**idéntica exista o no la cuenta** —mismo cuerpo y mismo tiempo—, por el mismo motivo que en el
registro: ver ADR-0008. Y recuperar la contraseña **no es recuperar la cuenta**: quien tenga el
segundo factor dado de alta sigue necesitándolo para entrar. Si no fuera así, el correo se
convertiría en un único punto de fallo y todo lo construido en `HU-22` sería decorativo.

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
