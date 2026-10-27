# Reporte de calidad funcional · ISO/IEC 25010

**Historia:** AYNI-219 (T-19, capítulo 12 del informe del APF3) · **Corte:** 27-oct-2026, Sprint 5 en
curso (de 8) · **Métricas:** ISO/IEC 25023 para la característica *adecuación funcional* de ISO/IEC
25010:2023 · **Datos:** historias y criterios de aceptación de Jira, y la ejecución de CI de
`develop` [40452694016](https://github.com/LOAD-13/ayni-bank/actions/runs/40452694016) descrita en el
[plan de pruebas](plan-de-pruebas.md).

---

## 1. Qué se mide y con qué escala

La adecuación funcional tiene tres subcaracterísticas. Todas se miden con la forma de ISO 25023,
**X = 1 − A / B**, que va de 0 (nada) a 1 (todo).

| Subcaracterística | Pregunta | A | B |
|---|---|---|---|
| **Completitud funcional** | ¿Está todo lo que se especificó? | Funciones especificadas que faltan | Funciones especificadas |
| **Corrección funcional** | ¿Lo que está da el resultado correcto? | Funciones con resultado incorrecto | Funciones evaluadas |
| **Pertinencia funcional** | ¿Las funciones permiten lograr lo que el cliente viene a hacer? | Funciones que faltan o fallan para un objetivo de uso | Funciones que ese objetivo necesita |

**Nivel de cumplimiento** (criterio del equipo, fijado antes de medir):

| X | Nivel |
|---|---|
| ≥ 0,90 | **Alto**: cumple |
| 0,75 – 0,89 | **Aceptable**: cumple con observaciones |
| 0,50 – 0,74 | **Parcial**: hay brechas que se planifican |
| < 0,50 | **Insuficiente** |

**Unidad de medida.** Una *función* es un criterio de aceptación de Jira (un escenario
DADO/CUANDO/ENTONCES). A nivel de producto se informa también por historia de usuario. Las historias
no funcionales (HU-14 a HU-20) no entran en esta característica: se evalúan en sus propias
características de ISO 25010 (eficiencia, fiabilidad, seguridad, usabilidad, mantenibilidad).

## 2. Completitud funcional

### 2.1 Por historia de usuario

De las 22 historias, 15 son funcionales.

| Alcance | Especificadas (B) | Faltan (A) | X | Nivel |
|---|---:|---:|---:|---|
| Comprometidas en los sprints ya cerrados (0 a 4) | 11 | 1 (HU-22) | **0,91** | Alto |
| Backlog funcional completo (hasta el Sprint 8) | 15 | 5 | **0,67** | Parcial: esperado a mitad del proyecto |

| HU | Historia | Sprint | Estado |
|---|---|---|---|
| HU-01 | Registro de usuario | 1 | Listo |
| HU-02 | Verificación de identidad con la foto del DNI | 2 | Listo |
| HU-03 | Verificación biométrica facial con selfie | 2 | Listo |
| HU-04 | Inicio de sesión seguro con segundo factor | 1 | Listo |
| HU-05 | Apertura automática de cuenta de ahorro | 1 | Listo |
| HU-06 | Rendimiento diario del saldo con TREA | 3 | Listo |
| HU-07 | Transferencia entre cuentas Ayni | 4 | Listo |
| HU-08 | Consulta de saldo e historial de movimientos | 3 | Listo, con una brecha (2.2) |
| HU-13 | Notificación por correo de cada movimiento | 4 | Listo |
| HU-21 | Recuperación de la contraseña | 2 | Listo |
| HU-22 | Segundo factor a elección y verificación del contacto | 2 → 5 | En curso |
| HU-09 | Emisión de tarjeta de débito virtual | 5 | Por hacer (sprint en curso) |
| HU-10 | Control de la tarjeta | 5 | En curso: web hecha, falta el backend |
| HU-11 | Transferencia interbancaria por CCI | 5 | Por hacer (sprint en curso) |
| HU-12 | Cuenta en dólares y conversión | 6 | Por hacer |

### 2.2 Por criterio de aceptación de las historias en «Listo»

| HU | Criterios (escenarios) | Implementados | Automatizados en Gherkin | Cubiertos con otras pruebas |
|---|---:|---:|---:|---|
| HU-01 | 4 | 4 | 4 | — |
| HU-02 | 5 | 5 | 5 | — |
| HU-03 | 5 | 5 | 0 | Unitarias e integración de kyc e identity (rostro único y vivacidad, cotejo, consentimiento) |
| HU-04 | 4 | 4 | 0 | Unitarias e integración de identity (credenciales, bloqueo, reutilización del token de renovación) |
| HU-05 | 4 | 4 | 4 | — |
| HU-06 | 4 | 4 | 4 | — |
| HU-07 | 5 | 5 | 0 | CP-CORE-025, -032 y -033; concurrencia contra PostgreSQL real (CP-CORE-149 y -150) |
| HU-08 | 2 | **1** | 1 (+1 `@pendiente`) | — |
| HU-13 | 3 | 3 | 3 | — |
| HU-21 | 5 | 5 | 5 | — |
| **Total** | **41** | **40** | **26** | **14** |

**Completitud por criterios: X = 1 − 1/41 = 0,976 · Alto.**

La brecha es el escenario 1 de HU-08, *consulta de movimientos con filtro*. El extracto no filtra
por rango de fechas, tipo ni importe, no pagina y no informa el saldo posterior de cada movimiento.
No se ocultó: está escrito como escenario `@pendiente` en `saldo-e-historial.feature`, que la suite
de aceptación omite a propósito y la CI cuenta como omitido. La historia figura en «Listo» con esa
deuda reconocida.

Los catorce escenarios de HU-03, HU-04 y HU-07 se comprueban con pruebas unitarias y de integración,
pero todavía no como Gherkin ejecutable. La Definition of Done exige Gherkin: el de HU-04 y HU-07 es
AYNI-229 y AYNI-231 (Sprint 5).

## 3. Corrección funcional

| Medida | Evaluadas (B) | Incorrectas (A) | X | Nivel |
|---|---:|---:|---:|---|
| Ejecuciones de prueba en la CI de `develop` | 1 236 | 0 | **1,00** | Alto |
| Criterios implementados con resultado correcto | 40 | 0 | **1,00** | Alto |
| Defectos funcionales abiertos en Jira | — | 0 | — | — |

Dos omisiones no cuentan como evaluadas: el escenario `@pendiente` de HU-08 (su función no existe;
cuenta en la completitud) y una prueba de tamaño táctil que solo se mide en el celular.

Que la cifra sea 1,00 no significa que nunca hubo defectos. Significa que los encontrados se
corrigieron antes de llegar a `develop` o en cuanto se detectaron. En este ciclo:

| Defecto | Cómo se detectó | Corregido en |
|---|---|---|
| Las defensas del ingreso y del código OTP se revertían con la transacción (AYNI-161) | Registrado como *bug* de seguridad en Jira | Sprint 4 |
| La estimación mensual del panel truncaba en lugar de redondear con HALF_EVEN (3,18 en vez de 3,19), y la proyección anual usaba coma flotante (AYNI-233 y AYNI-234) | Auditoría de idoneidad funcional de la semana 10 | PR #73 |
| «Corregir» devolvía vacío el formulario de transferencia | Suite de usabilidad T-21 | PR #81 |
| «Iniciar sesión» y «Entrar a mi banca» llevaban a una página de «todavía no está lista» | Suite de usabilidad T-21 | PR #81 |
| Orden del foco del registro en el celular (WCAG 2.4.3) | Suite de usabilidad T-21 | PR #81 |
| El foco no se distinguía en el botón de la contraseña (WCAG 2.4.7) | Suite de usabilidad T-21 | PR #81 |
| Las animaciones ignoraban «reducir movimiento» (WCAG 2.3.3) | Suite de usabilidad T-21 | PR #81 |

## 4. Pertinencia funcional

Se mide contra lo que el cliente viene a hacer, no contra el backlog. Cada objetivo de uso necesita
un conjunto de funciones. Si falta una, el objetivo no se logra por completo.

| Objetivo de uso | Funciones que necesita | Faltan | X |
|---|---|---|---:|
| **O1 · Abrir una cuenta sin ir a una agencia** | Registro (HU-01) · DNI (HU-02) · selfie (HU-03) · cuenta automática (HU-05) | — | **1,00** |
| **O2 · Entrar de forma segura** | Ingreso con segundo factor (HU-04) · recuperar la contraseña (HU-21) · elegir el método del segundo factor (HU-22) | Elegir el método | **0,67** |
| **O3 · Saber cuánto tengo y qué pasó con mi dinero** | Saldo · historial · filtrar movimientos (HU-08) · rendimiento diario (HU-06) | Filtrar | **0,75** |
| **O4 · Enviar dinero** | Entre cuentas Ayni con comprobante (HU-07) · aviso por correo (HU-13) · a otros bancos por CCI (HU-11) | Interbancaria | **0,67** |
| **O5 · Pagar con tarjeta** | Emitir la tarjeta virtual (HU-09) · congelarla y limitarla (HU-10) | Ambas | **0,00** |
| **O6 · Ahorrar en dólares** | Cuenta en dólares y conversión (HU-12) | Toda | **0,00** |

| Alcance | X media | Nivel |
|---|---:|---|
| Objetivos con todas sus historias planificadas hasta el Sprint 4 o antes, más O4 (O1–O4) | **0,77** | Aceptable |
| Los seis objetivos del producto | **0,51** | Parcial: O5 y O6 son de los sprints 5 y 6 |

Lectura: el cliente **ya puede abrir su cuenta, entrar, ver su dinero y transferir a otro cliente de
Ayni de principio a fin**. Le faltan tres cosas: filtrar el extracto, transferir a otros bancos y la
tarjeta. Dos de ellas están en el sprint en curso.

## 5. Resumen

| Subcaracterística | Medida principal | X | Nivel |
|---|---|---:|---|
| Completitud funcional | Criterios de aceptación de las HU entregadas | 0,976 | **Alto** |
| Completitud funcional | HU comprometidas en sprints cerrados | 0,91 | **Alto** |
| Corrección funcional | Ejecuciones de prueba en `develop` | 1,00 | **Alto** |
| Pertinencia funcional | Objetivos de uso O1–O4 | 0,77 | **Aceptable** |

**Nivel de cumplimiento global de la adecuación funcional del incremento entregado: Alto en
completitud y corrección, Aceptable en pertinencia.** La pertinencia sube con tres entregas
concretas: el filtro del extracto (deuda de HU-08), HU-11 y HU-22. Las dos últimas ya están en el
Sprint 5.

### Acciones derivadas

| Acción | Dónde |
|---|---|
| Implementar el filtro y la paginación del extracto (escenario 1 de HU-08) y quitar el `@pendiente` | Nueva tarea de HU-08 |
| Automatizar en Gherkin los escenarios de HU-04 y HU-07 | AYNI-229 y AYNI-231 |
| Automatizar en Gherkin los escenarios de HU-03 a partir de `tests/onboarding_kyc.feature` | Nueva tarea de HU-03 |
| Repetir esta medición al cierre del Sprint 8, para el informe final | T-19 del PROY |
