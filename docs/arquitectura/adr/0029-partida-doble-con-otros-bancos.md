# ADR-0029 · Partida doble con otros bancos: cuenta de compensación interbancaria

- **Estado:** Aceptado
- **Fecha:** 2026-10-02
- **Refs:** AYNI-158 · complementa ADR de libro mayor y la migración V3 (cuenta de fondeo)

## Contexto

En la revisión de la base de datos el Product Owner preguntó qué ocurre cuando una
transferencia llega desde otro banco: si el dinero no sale de una cuenta Ayni, ¿se
registra un solo asiento?

Si se registrara uno solo, el libro dejaría de cuadrar: la suma de todos los asientos ya no
sería cero, el saldo del cliente subiría sin contrapartida y no habría forma de conciliar
con la Cámara de Compensación Electrónica (CCE) lo que el banco recibió y lo que le deben
liquidar.

## Decisión

**Todo movimiento tiene siempre dos asientos, también los interbancarios.** Cuando la
contraparte está fuera de Ayni Bank, la representa una **cuenta técnica de compensación
interbancaria** (`cce00000-0000-4000-8000-000000000001`, número `0011-0000000002`), sembrada
por la migración V4:

| Operación | Cargo | Abono |
|---|---|---|
| Transferencia entrante desde otro banco | Compensación CCE | Cuenta del cliente |
| Transferencia saliente a otro banco | Cuenta del cliente | Compensación CCE |
| Depósito simulado (ya existente, V3) | Fondeo | Cuenta del cliente |

El saldo de la cuenta de compensación es, en cada momento, la **posición neta frente a la
CCE**. Al liquidar cada ciclo, un movimiento contra la cuenta del banco en el BCRP la deja en
cero; si no vuelve a cero, hay una diferencia que conciliar.

Las cuentas técnicas tienen titulares reservados (`00000000-0000-0000-0000-00000000000N`).
Identity genera UUID v4, cuyos 64 bits altos nunca son cero, así que ningún cliente coincide;
el adaptador excluye estas cuentas de la búsqueda por número y nadie puede transferirles.

## Consecuencias

- El invariante «la suma de los asientos de un movimiento es cero» se mantiene sin
  excepciones, y con él la conciliación y la auditoría.
- La integración con la CCE (HU de transferencias interbancarias) solo tendrá que construir
  el movimiento con esta contrapartida: el modelo de datos ya está listo.
- La misma migración añade `evento_auditoria` en el schema core, que registra cada operación
  y cada rechazo, como ya hacía identity con los ingresos.
