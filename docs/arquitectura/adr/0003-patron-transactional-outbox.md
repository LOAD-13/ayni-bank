# ADR-0003 · Patrón Transactional Outbox para eventos de dominio

**Estado:** Aceptada
**Fecha:** 14 de agosto de 2026

---

## Contexto

Cuando se completa una transferencia hay que notificar al cliente. La notificación la envía un
servicio distinto (`ayni-notification-service`) a través de RabbitMQ.

El problema es la **escritura dual**: mover el dinero en PostgreSQL y publicar un evento en
RabbitMQ son dos operaciones sobre dos sistemas distintos. No hay forma de hacerlas atómicas
directamente, y ambos órdenes fallan:

- **Publicar y luego confirmar:** si la transacción hace rollback, ya se notificó una transferencia
  que nunca ocurrió. El cliente recibe un correo de un movimiento inexistente.
- **Confirmar y luego publicar:** si el broker está caído o el proceso muere entre ambas, la
  transferencia ocurrió pero nadie se enteró. El evento se pierde para siempre.

## Opciones consideradas

**A. Publicar directamente tras el commit** — simple, pero pierde eventos ante fallo del broker.

**B. Transacción distribuida (XA / 2PC)** — atómica de verdad, pero RabbitMQ tiene soporte limitado,
introduce bloqueos y penaliza gravemente el rendimiento.

**C. Transactional Outbox** — el evento se escribe en una tabla de la misma base de datos, dentro
de la misma transacción; un publicador aparte lo entrega al broker.

## Decisión

**Opción C — Transactional Outbox.**

```
core-banking-service · UNA SOLA transacción ACID
  1. debitar cuenta origen
  2. acreditar cuenta destino
  3. escribir asientos en el libro mayor
  4. INSERT en outbox_event          ← mismo COMMIT
  COMMIT
        ↓
  publicador lee outbox → publica en RabbitMQ → marca como enviado
        ↓
  notification-service consume (idempotente por eventId) → envía correo
```

## Justificación

| Escenario | Resultado |
|---|---|
| La transferencia hace rollback | El evento desaparece con el rollback. **Nunca se notifica algo que no ocurrió.** |
| RabbitMQ está caído | El evento permanece en la tabla y se publica al restablecerse. **Nunca se pierde.** |
| El publicador entrega dos veces | El consumidor deduplica por `eventId`. **Un solo correo.** |
| El proceso muere tras el commit | El publicador retoma los eventos pendientes al reiniciar. |

La entrega es **at-least-once**, por lo que la idempotencia del consumidor es obligatoria, no
opcional.

## Consecuencias

**A favor** — Garantía real de que no se pierden ni se inventan eventos. Es material directo para
evidenciar *fiabilidad* y *tolerancia a fallos* en el informe de ISO/IEC 25010. Es exactamente como
lo resuelven los sistemas bancarios en producción.

**En contra** — Una tabla más y un proceso publicador que mantener y monitorizar. Latencia añadida
entre el commit y la entrega del evento (segundos, aceptable para notificaciones). La tabla
`outbox_event` requiere purga periódica de eventos ya entregados.

**Monitorización obligatoria** — alerta si hay eventos pendientes con antigüedad superior a cinco
minutos: significa que el publicador o el broker tienen un problema.
