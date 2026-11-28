# Reporte de calidad funcional · ISO/IEC 25010

**Historia:** AYNI-219 (T-19, capítulo 12 del informe del APF3) · **Corte:** 30-oct-2026, cierre del
Sprint 5 (de 8) y versión 2.0.0 en producción · **Métricas:** ISO/IEC 25023 para la característica *adecuación funcional* de ISO/IEC
25010:2023 · **Datos:** historias y criterios de aceptación de Jira, la CI del PR de la release 2.0.0
[40967550502](https://github.com/LOAD-13/ayni-bank/actions/runs/40967550502), una ejecución limpia
de `mvn clean verify` y de Playwright sobre la 2.0.0, y el [plan de pruebas](plan-de-pruebas.md).

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

De las 22 historias, 15 son funcionales. Estado en Jira al 30-oct-2026.

| Alcance | Especificadas (B) | Faltan (A) | X | Nivel |
|---|---:|---:|---:|---|
| Comprometidas en los sprints ya cerrados (0 a 5) | 14 | 0 | **1,00** | Alto |
| Backlog funcional completo (hasta el Sprint 8) | 15 | 1 (HU-12) | **0,93** | Alto |

| HU | Historia | Sprint | Estado |
|---|---|---:|---|
| HU-01 | Registro de usuario | 1 | Listo |
| HU-02 | Verificación de identidad con la foto del DNI | 2 | Listo |
| HU-03 | Verificación biométrica facial con selfie | 2 | Listo |
| HU-04 | Inicio de sesión seguro con segundo factor | 1 | Listo |
| HU-05 | Apertura automática de cuenta de ahorro | 1 | Listo |
| HU-06 | Rendimiento diario del saldo con TREA | 3 | Listo |
| HU-07 | Transferencia entre cuentas Ayni | 4 | Listo |
| HU-08 | Consulta de saldo e historial de movimientos | 3 | Listo (el filtro del extracto llegó con el #94) |
| HU-09 | Emisión de tarjeta de débito virtual | 5 | Listo |
| HU-10 | Control de la tarjeta | 5 | Listo |
| HU-11 | Transferencia interbancaria por CCI | 5 | Listo |
| HU-13 | Notificación por correo de cada movimiento | 4 | Listo |
| HU-21 | Recuperación de la contraseña | 2 | Listo |
| HU-22 | Segundo factor a elección y verificación del contacto | 2 → 5 | Listo |
| HU-12 | Cuenta en dólares y conversión | 6 | Por hacer (sprint siguiente) |

### 2.2 Por criterio de aceptación de las historias en «Listo»

Cada criterio de aceptación es un escenario Gherkin que se ejecuta en la CI (Cucumber). Ningún
escenario lleva ya la etiqueta `@pendiente`.

| HU | Escenarios Gherkin | Ejecutables | Archivo |
|---|---:|---:|---|
| HU-01 | 5 | 5 | `registro-de-usuario.feature` |
| HU-02 | 6 | 6 | `verificacion-de-identidad-dni.feature` |
| HU-03 | 4 | 4 | `verificacion-biometrica-selfie.feature` |
| HU-04 | 4 | 4 | `inicio-de-sesion.feature` |
| HU-05 | 9 | 9 | `apertura-automatica-de-cuenta.feature`, `rechazo-de-solicitud.feature`, `aviso-de-solicitud-rechazada.feature` |
| HU-06 | 4 | 4 | `rendimiento-diario.feature` |
| HU-07 | 5 | 5 | `transferencia-entre-cuentas.feature` |
| HU-08 | 4 | 4 | `saldo-e-historial.feature` |
| HU-09 | 3 | 3 | `emision-de-tarjeta.feature` |
| HU-10 | 2 | 2 | `control-de-tarjeta.feature` |
| HU-11 | 7 | 7 | `transferencia-interbancaria.feature` |
| HU-13 | 3 | 3 | `notificacion-de-movimientos.feature` |
| HU-21 | 5 | 5 | `recuperacion-de-contrasena.feature` |
| HU-22 | 5 | 5 | `segundo-factor-a-eleccion.feature` |
| **Total** | **66** | **66** | |

**Completitud por criterios: X = 66 / 66 = 1,00 · Alto.**

Las acciones del corte anterior (27-oct) quedaron hechas: el filtro y la paginación del extracto
(escenario 1 de HU-08, ya sin `@pendiente`) y el Gherkin de HU-03, HU-04 y HU-07.

## 3. Corrección funcional

| Medida | Evaluadas (B) | Incorrectas (A) | X | Nivel |
|---|---:|---:|---:|---|
| Pruebas Java en `mvn clean verify` sobre la 2.0.0 (incluye los escenarios Cucumber) | 929 | 0 | **1,00** | Alto |
| Pruebas de interfaz Playwright (escritorio, ejecución local del 30-oct) | 107 | 0 | **1,00** | Alto |
| Escenarios Gherkin ejecutables | 66 | 0 | **1,00** | Alto |
| Defectos funcionales abiertos en Jira | — | 0 | — | — |

Una omisión no cuenta como evaluada: una prueba de tamaño táctil que solo se mide en el celular.

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
| **O2 · Entrar de forma segura** | Ingreso con segundo factor (HU-04) · recuperar la contraseña (HU-21) · elegir el método del segundo factor (HU-22) | — | **1,00** |
| **O3 · Saber cuánto tengo y qué pasó con mi dinero** | Saldo · historial · filtrar movimientos (HU-08) · rendimiento diario (HU-06) | — | **1,00** |
| **O4 · Enviar dinero** | Entre cuentas Ayni con comprobante (HU-07) · aviso por correo (HU-13) · a otros bancos por CCI (HU-11) | — | **1,00** |
| **O5 · Pagar con tarjeta** | Emitir la tarjeta virtual (HU-09) · congelarla y limitarla (HU-10) | — | **1,00** |
| **O6 · Ahorrar en dólares** | Cuenta en dólares y conversión (HU-12) | Toda | **0,00** |

| Alcance | X media | Nivel |
|---|---:|---|
| Objetivos con todas sus historias planificadas hasta el Sprint 5 (O1–O5) | **1,00** | Alto |
| Los seis objetivos del producto | **0,83** | Alto: O6 es del Sprint 6 |

Lectura: con la versión 2.0.0 el cliente **ya puede abrir su cuenta, entrar, ver y filtrar su dinero,
transferir a clientes de Ayni y a otros bancos, y usar su tarjeta virtual**. Falta la cuenta en
dólares (HU-12), planificada para el Sprint 6.

## 5. Resumen

| Subcaracterística | Medida principal | X | Nivel |
|---|---|---:|---|
| Completitud funcional | Criterios de aceptación de las HU entregadas | 1,00 | **Alto** |
| Completitud funcional | HU comprometidas en sprints cerrados | 1,00 | **Alto** |
| Corrección funcional | Pruebas ejecutadas sobre la 2.0.0 | 1,00 | **Alto** |
| Pertinencia funcional | Objetivos de uso O1–O5 | 1,00 | **Alto** |
| Pertinencia funcional | Los seis objetivos | 0,83 | **Alto** |

**Nivel de cumplimiento global de la adecuación funcional de la versión 2.0.0: Alto** en
completitud, corrección y pertinencia. Lo único pendiente es la cuenta en dólares (HU-12, Sprint 6).

### Acciones derivadas

| Acción | Dónde |
|---|---|
| Cuenta en dólares y conversión con tipo de cambio real (objetivo O6) | HU-12, Sprint 6 |
| Llevar la prueba de tamaño táctil también a la medición de escritorio | Mejora de la suite de usabilidad |

