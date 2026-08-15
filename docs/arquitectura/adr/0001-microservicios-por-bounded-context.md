# ADR-0001 · Microservicios por bounded context, no por entidad

**Estado:** Aceptada
**Fecha:** 14 de agosto de 2026
**Decide:** Joaquín Loa (Product Owner) con el equipo de proyecto

---

## Contexto

Ayni Bank debe ser escalable y demostrar una arquitectura distribuida. Había que decidir cuántos
servicios desplegar y por dónde cortarlos.

El equipo lo forman cuatro estudiantes con dedicación parcial durante diecisiete semanas, sin
experiencia previa en sistemas distribuidos.

En un sistema financiero, la partición tiene una consecuencia que no tiene en otros dominios: si
cuentas, asientos contables y transferencias viven en servicios distintos, una transferencia deja
de ser una transacción ACID y se convierte en una **saga** con compensaciones, estados intermedios
y necesidad de idempotencia en cada paso.

## Opciones consideradas

**A. Monolito modular** — un desplegable con módulos internos estrictos.
Simple de operar, escala por réplicas, transaccionalidad trivial. No demuestra arquitectura
distribuida.

**B. Microservicios granulares (6-7 servicios)** — separar cuentas, tarjetas, transferencias y
ledger.
Máxima demostración de desacoplamiento. Obliga a sagas en el flujo de dinero, patrón outbox
distribuido, y siete pipelines para cuatro personas.

**C. Microservicios por bounded context de negocio (5 servicios)** — el dinero junto, el resto
separado.

## Decisión

**Opción C.** Cinco servicios:

| Servicio | Responsabilidad |
|---|---|
| `ayni-gateway` | Enrutado, rate limiting, validación de JWT |
| `ayni-identity-service` | Registro, autenticación, MFA, orquestación del onboarding |
| `ayni-core-banking-service` | Cuentas, ledger, tarjetas, transferencias |
| `ayni-kyc-service` | Visión por computadora (Python) |
| `ayni-notification-service` | Correo y notificaciones por eventos |

El criterio de corte es el **bounded context de negocio**, no la entidad.

## Justificación

**El dinero viaja junto.** Cuentas, asientos y transferencias comparten límite transaccional. Una
transferencia es un `@Transactional` con bloqueo pesimista, no una coreografía distribuida. Esta es
la fuente número uno de dinero duplicado o desaparecido en sistemas financieros reales.

**Las notificaciones sí se separan**, porque no mueven dinero: si fallan se reintentan, si se
duplican molestan pero no cuestan. Separarlas *reduce* riesgo en lugar de aumentarlo.

**La visión por computadora se separa** porque es Python: OCR y reconocimiento facial en Java son
notablemente más costosos de implementar y de peor calidad.

## Consecuencias

**A favor** — Arquitectura distribuida real, escalable por servicio de forma independiente.
Transferencias con garantías ACID. Cinco pipelines, no siete. El servicio de visión usa el
ecosistema adecuado.

**En contra** — `core-banking-service` es el servicio más grande y podría necesitar dividirse si el
sistema creciera. La comunicación entre `identity` y `core` es eventualmente consistente y exige el
patrón de proyección local (ver ADR-0004).

**Se revisará si** — `core-banking-service` supera las 15 000 líneas o si algún subdominio necesita
escalar de forma marcadamente distinta al resto.
