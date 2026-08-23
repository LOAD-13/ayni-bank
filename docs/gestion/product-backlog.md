# Product Backlog — Ayni Bank

**Versión 1.0** · 15 de agosto de 2026
**Product Owner:** Dr. Carlos R. P. Tovar  ·  **Scrum Master:** Joaquín Alfonso Loa Denegri
**Herramienta de gestión:** Jira — proyecto [`AYNI`](https://jloadenegri.atlassian.net/jira/software/projects/AYNI)

> **Fuente de verdad: Jira.** Este documento es una fotografía legible y versionada del Product
> Backlog en la fecha indicada. Ante cualquier discrepancia, manda Jira. Se regenera cuando el
> backlog cambia de forma sustantiva (nuevo ítem, repriorización, cambio de estimación).

---

## 1. Qué es y qué no es

El Product Backlog es la **fuente única de requerimientos del producto**: una lista ordenada de todo
lo que Ayni Bank necesita — historias de usuario, defectos y mejoras. Es **dinámico**: el Product
Owner lo actualiza y prioriza de forma continua, y los ítems superiores están más detallados
(*refinados*) que los inferiores.

No es un contrato ni una lista de tareas técnicas. El plan técnico de cada sprint vive en su
**Sprint Backlog** (`docs/gestion/sprint-backlog/`), que es propiedad exclusiva de los Developers.

**Compromiso asociado — Product Goal:**

> Que cualquier persona en Perú pueda abrir una cuenta de ahorro remunerada desde el navegador en
> menos de 90 segundos, sin acudir a una agencia y sin pagar comisión de mantenimiento, con la
> integridad contable y la trazabilidad que exige un sistema financiero.

---

## 2. Resumen

| Concepto | Valor |
|---|---|
| Historias de Usuario | 20 (13 funcionales · 7 no funcionales) |
| Tareas técnicas y de gestión | 9 |
| **Total de ítems** | **29** |
| **Total estimado** | **231 puntos de historia** |
| Épicas | 11 |
| Sprints planificados | 9 (Sprint 0 de 1 semana + 8 de 2 semanas) |
| Horizonte | 14 ago – 13 dic 2026 |

**Escala de estimación:** Fibonacci (1, 2, 3, 5, 8, 13, 21). Un punto no es una hora: es una medida
relativa de esfuerzo, complejidad e incertidumbre combinados.

---

## 3. Criterio de priorización

El orden **no** es arbitrario ni cronológico. Se prioriza por esta jerarquía:

1. **Riesgo técnico primero.** Lo que puede hacer fracasar el proyecto se ataca cuando aún hay
   tiempo de reaccionar. Por eso el esqueleto ambulante del Sprint 1 va antes que cualquier
   funcionalidad rica: valida que los cinco servicios se entienden en la semana 4, no en la 14.
2. **Dependencias duras.** No se puede abrir una cuenta sin identidad verificada, ni transferir sin
   libro mayor.
3. **Valor para el cliente.** A igualdad de riesgo y dependencias, primero lo que el cliente nota.
4. **Coste de retrasarlo.** Lo transversal (auditoría, accesibilidad) es más caro de añadir al
   final, pero necesita que exista algo que auditar.

**Regla de alcance:** toda incorporación al backlog exige **retirar un ítem de esfuerzo
equivalente**, salvo aprobación explícita del patrocinador. Es la mitigación del riesgo R-03.

---

## 4. Backlog priorizado

Leyenda de estado: ✅ Hecho · 🟡 En curso · ⬜ Por hacer

| # | Clave | Ítem | Tipo | Épica | Prioridad | Pts | Sprint | Estado |
|---|---|---|---|---|---|---|---|---|
| 1 | `AYNI-32` | T-01 · Acta de Constitución del Proyecto | Tarea | EP-00 | Muy alta | 5 | S0 | ✅ |
| 2 | `AYNI-33` | T-02 · Diagrama de Gantt híbrido | Tarea | EP-00 | Muy alta | 3 | S0 | ✅ |
| 3 | `AYNI-34` | T-03 · Matriz de Gestión de Riesgos | Tarea | EP-00 | Muy alta | 5 | S0 | ✅ |
| 4 | `AYNI-35` | T-04 · Matriz de Gestión de Interesados | Tarea | EP-00 | Alta | 3 | S0 | ✅ |
| 5 | `AYNI-37` | T-06 · Monorepo con GitFlow y ramas protegidas | Tarea | EP-01 | Muy alta | 3 | S0 | ✅ |
| 6 | `AYNI-40` | T-09 · Documentos guía del repositorio | Tarea | EP-01 | Alta | 5 | S0 | ✅ |
| 7 | `AYNI-39` | T-08 · Identidad visual y design system | Tarea | EP-01 | Media | 5 | S0 | ✅ |
| 8 | `AYNI-30` | HU-19 · [NF] Verificación automática de calidad en cada PR | Historia | EP-01 | Muy alta | 8 | S0 | ✅ |
| 9 | `AYNI-36` | T-05 · Product Backlog priorizado y adquisiciones | Tarea | EP-00 | Alta | 3 | S0 | 🟡 |
| 10 | `AYNI-29` | HU-18 · [NF] Entorno reproducible con un solo comando | Historia | EP-01 | Muy alta | 8 | S0 | 🟡 |
| 11 | `AYNI-38` | T-07 · Esqueleto hexagonal de los cinco servicios | Tarea | EP-01 | Muy alta | 8 | S0 | ⬜ |
| 12 | `AYNI-31` | HU-20 · [NF] Despliegue continuo con aprobación | Historia | EP-01 | Muy alta | 13 | S1 | ⬜ |
| 13 | `AYNI-12` | HU-01 · Registro de usuario en Ayni Bank | Historia | EP-02 | Muy alta | 5 | S1 | ⬜ |
| 14 | `AYNI-15` | HU-04 · Inicio de sesión seguro con segundo factor | Historia | EP-02 | Muy alta | 8 | S1 | ⬜ |
| 15 | `AYNI-16` | HU-05 · Apertura automática de cuenta de ahorro | Historia | EP-03 | Muy alta | 8 | S1 | ⬜ |
| 16 | `AYNI-13` | HU-02 · Verificación de identidad mediante DNI | Historia | EP-02 | Muy alta | 13 | S2 | ⬜ |
| 17 | `AYNI-14` | HU-03 · Verificación biométrica facial | Historia | EP-02 | Muy alta | 13 | S2 | ⬜ |
| 18 | `AYNI-17` | HU-06 · Rendimiento diario del saldo con TREA | Historia | EP-03 | Muy alta | 13 | S3 | ⬜ |
| 19 | `AYNI-19` | HU-08 · Consulta de saldo e historial | Historia | EP-03 | Alta | 5 | S3 | ⬜ |
| 20 | `AYNI-18` | HU-07 · Transferencia entre cuentas Ayni | Historia | EP-05 | Muy alta | 13 | S4 | ⬜ |
| 21 | `AYNI-24` | HU-13 · Notificación por correo de cada movimiento | Historia | EP-07 | Media | 8 | S4 | ⬜ |
| 22 | `AYNI-20` | HU-09 · Emisión de tarjeta de débito virtual | Historia | EP-04 | Alta | 8 | S5 | ⬜ |
| 23 | `AYNI-21` | HU-10 · Control de la tarjeta: congelar y limitar | Historia | EP-04 | Media | 5 | S5 | ⬜ |
| 24 | `AYNI-22` | HU-11 · Transferencia interbancaria mediante CCI | Historia | EP-05 | Alta | 13 | S6 | ⬜ |
| 25 | `AYNI-23` | HU-12 · Cuenta en dólares y tipo de cambio real | Historia | EP-08 | Media | 13 | S6 | ⬜ |
| 26 | `AYNI-26` | HU-15 · [NF] Trazabilidad y pista de auditoría | Historia | EP-09 | Alta | 8 | S7 | ⬜ |
| 27 | `AYNI-28` | HU-17 · [NF] Accesibilidad y facilidad de aprendizaje | Historia | EP-06 | Media | 8 | S7 | ⬜ |
| 28 | `AYNI-25` | HU-14 · [NF] Tiempo de respuesta bajo carga | Historia | EP-10 | Alta | 8 | S8 | ⬜ |
| 29 | `AYNI-27` | HU-16 · [NF] Disponibilidad y recuperación ante fallos | Historia | EP-10 | Alta | 13 | S8 | ⬜ |

> **Nota sobre el campo Prioridad:** el proyecto en Jira es *team-managed* y no expone el campo
> `Prioridad`. La prioridad de negocio se consigna en la descripción de cada ítem y, sobre todo, en
> **el orden de esta lista** — que es donde Scrum establece que vive realmente la priorización.

---

## 5. Distribución por sprint

| Sprint | Periodo | Ítems | Puntos | Meta |
|---|---|---|---|---|
| Sprint 0 | 17–23 ago | 11 | 56 | Fundación lista para construir |
| Sprint 1 | 24 ago – 6 sep | 4 | 34 | Esqueleto ambulante extremo a extremo |
| Sprint 2 | 7–20 sep | 2 | 26 | Onboarding KYC real |
| Sprint 3 | 21 sep – 4 oct | 2 | 18 | Núcleo transaccional y rendimiento |
| Sprint 4 | 5–18 oct | 2 | 21 | Transferencias y notificaciones |
| Sprint 5 | 19 oct – 1 nov | 2 | 13 | Tarjeta de débito virtual |
| Sprint 6 | 2–15 nov | 2 | 26 | Interoperabilidad y multimoneda |
| Sprint 7 | 16–29 nov | 2 | 16 | Operación, auditoría y accesibilidad |
| Sprint 8 | 30 nov – 13 dic | 2 | 21 | Endurecimiento y cierre |

**Velocidad prevista:** unos 22 puntos por sprint de dos semanas, con un equipo de seis Developers. Es una previsión, no un
compromiso: se recalcula con los datos reales al cerrar cada sprint.

---

## 6. Épicas

| Épica | Clave | Bloque de valor | Sprints |
|---|---|---|---|
| EP-00 · Iniciación y planificación | `AYNI-1` | Acta, cronograma, matrices, línea base | S0 |
| EP-01 · Fundación técnica y gobernanza | `AYNI-2` | Repositorio, CI/CD, contenedores, arquitectura base | S0–S1 |
| EP-02 · Identidad digital y onboarding KYC | `AYNI-3` | Registro, autenticación, DNI, biometría | S1–S2 |
| EP-03 · Cuentas y núcleo transaccional | `AYNI-4` | Cuentas, libro mayor, devengo, TREA | S1–S3 |
| EP-04 · Tarjeta de débito virtual | `AYNI-5` | Emisión y controles del cliente | S5 |
| EP-05 · Transferencias e interoperabilidad | `AYNI-6` | Internas, interbancarias, compensación | S4–S6 |
| EP-06 · Experiencia del cliente | `AYNI-7` | Landing, panel, estados de cuenta, accesibilidad | S7 |
| EP-07 · Notificaciones y comunicaciones | `AYNI-8` | Outbox, eventos, correo transaccional | S4 |
| EP-08 · Multimoneda y tipo de cambio | `AYNI-9` | Cuenta en dólares, conversión, SUNAT | S6 |
| EP-09 · Operación y observabilidad | `AYNI-10` | Back-office, auditoría, métricas, alertas | S7 |
| EP-10 · Calidad, endurecimiento y cierre | `AYNI-11` | Estrés, alta disponibilidad, recuperación | S8 |

---

## 7. Refinamiento

El backlog se refina **a mitad de cada sprint**, en una sesión de una hora. Se revisan los ítems de
los dos sprints siguientes para que lleguen al Sprint Planning en condiciones de ser
comprometidos.

Un ítem está **refinado** cuando tiene: descripción en formato `Como [rol] quiero [acción] para
[beneficio]`, criterios de aceptación en formato DADO/CUANDO/ENTONCES sin ambigüedad, estimación
en puntos acordada por los Developers, y dependencias identificadas.

Los ítems no refinados **no pueden comprometerse** en un sprint.

---

## 8. Fuera de alcance

Declarado en el Acta de Constitución y **no incorporable sin aprobación del patrocinador**:

Aplicativo móvil nativo · billetera digital y pagos por QR · productos de crédito · depósitos a
plazo y fondos mutuos · agencias y cajeros · tarjetas físicas · integración productiva con RENIEC y
con la Cámara de Compensación Electrónica · autorización ante la SBS.

---

## 9. Historial de versiones

| Versión | Fecha | Cambios |
|---|---|---|
| 1.0 | 15 ago 2026 | Versión inicial. 29 ítems, 231 puntos, 11 épicas, 9 sprints. |
