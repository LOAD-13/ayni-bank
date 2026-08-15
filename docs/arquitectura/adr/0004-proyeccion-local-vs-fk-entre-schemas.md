# ADR-0004 · Proyección local en lugar de claves foráneas entre schemas

**Estado:** Aceptada
**Fecha:** 14 de agosto de 2026

---

## Contexto

`core-banking-service` necesita saber a qué cliente pertenece una cuenta, pero los datos del cliente
son propiedad de `identity-service`. Ambos servicios usan schemas distintos (`identity` y `core`)
sobre la misma instancia de PostgreSQL.

Existe además una tensión con el entregable académico: los microservicios prescriben «base de datos
por servicio, sin claves foráneas entre servicios», mientras que un diagrama entidad-relación
extendido (EER) espera ver todas las entidades conectadas.

## Opciones consideradas

**A. Clave foránea entre schemas** — `core.cuenta.cliente_id → identity.persona.id`.
PostgreSQL lo soporta. Integridad garantizada por el motor, cero código de sincronización, y el
modelo físico coincide con el lógico. Pero **acopla los servicios a nivel de base de datos**:
dejarían de ser independientes y separar las bases en el futuro exigiría refactorizar.

**B. Solo referencia por identificador, sin FK ni copia** — `core.cuenta` guarda un `cliente_id`
suelto y consulta a `identity-service` por REST cuando necesita datos. Máxima pureza, pero cada
listado dispara llamadas entre servicios (N+1 distribuido) y nada impide un `cliente_id` apuntando
a un cliente inexistente.

**C. Proyección local sincronizada por eventos** — `core` mantiene su propia tabla `cliente` con
los datos mínimos que necesita, alimentada por eventos de `identity`.

## Decisión

**Opción C — proyección local.**

`core-banking-service` posee una tabla `core.cliente` con los datos mínimos necesarios
(identificador, nombres, documento enmascarado, estado), sincronizada mediante eventos publicados
por `identity-service`. La relación `core.cuenta.cliente_id → core.cliente.id` es una **clave
foránea real dentro del mismo schema**.

## Justificación

**Cada schema queda internamente completo**, con integridad referencial efectiva y sin registros
huérfanos. No hay consultas entre servicios para operaciones habituales.

**Los servicios siguen siendo independientes de verdad**: separar las bases de datos mañana no
requiere tocar código, solo configuración.

**Resuelve la tensión del EER** distinguiendo dos planos, que es como se documenta en cualquier
organización con arquitectura distribuida:

| Modelo | Qué muestra | Para qué |
|---|---|---|
| **Lógico** | Un único diagrama con las ~25 entidades y **todas** sus relaciones, incluidas las que cruzan contextos | Es el EER que se entrega. Visión conceptual del negocio. |
| **Físico** | Tres schemas con proyecciones locales y FK internas | Es lo que se implementa. |

Ambos son correctos en su plano y ambos se documentan.

## Consecuencias

**A favor** — Integridad referencial real dentro de cada contexto. Sin N+1 distribuido. Los
servicios pueden separar sus bases sin refactorizar. Aporta un caso de estudio de consistencia
eventual valioso para el informe.

**En contra** — Duplicación controlada de datos del cliente. Hay que implementar y monitorizar la
sincronización por eventos. Existe una ventana de inconsistencia entre que `identity` actualiza un
dato y `core` lo refleja.

**Mitigación** — La proyección guarda solo datos que cambian con muy poca frecuencia (nombres,
estado). Los datos volátiles o sensibles nunca se proyectan: se consultan al servicio propietario
cuando de verdad se necesitan.
