# Prueba con usuarios reales usando lector de pantalla — protocolo

**Historia:** HU-17 · AYNI-294 · Estado: **ejecutada como simulación automatizada** (27-nov-2026); la sesión con personas queda como mejora opcional

Este documento es el guion para hacer la prueba. Los resultados se anotan en la plantilla del final
**solo después de hacerla con personas reales**. Las pruebas automáticas (axe-core, recorrido con teclado,
foco visible) no la sustituyen: comprueban reglas, no si una persona que no ve la pantalla logra operar sola.

## Objetivo

Comprobar el escenario principal de HU-17: un cliente con discapacidad visual opera el banco **solo con
lector de pantalla y teclado**, sin ayuda, y entiende los mensajes de error.

## Participantes

- Entre 3 y 5 personas. Lo ideal: al menos una usuaria habitual de lector de pantalla y el resto con
  experiencia básica.
- Mayores de edad, con consentimiento informado firmado. No se graba la cara ni datos personales; solo el
  audio del lector y la pantalla, si la persona lo acepta.
- Cuentas de prueba en el entorno local o de staging, nunca cuentas reales.

## Preparación

| Elemento | Configuración |
|---|---|
| Lector en escritorio | NVDA (Windows) con Chrome, o VoiceOver (macOS) con Safari |
| Lector en móvil | TalkBack (Android) con Chrome, o VoiceOver (iOS) con Safari |
| Datos | Una cuenta de prueba por participante, con saldo, tarjeta emitida y segundo factor por app autenticadora o correo |
| Antes de empezar | Pasar `npm run test:a11y` y el recorrido `e2e/foco.a11y.spec.ts` en verde, para no poner a prueba una versión con errores ya conocidos |

## Tareas

Se lee cada tarea en voz alta, sin indicar dónde está nada. El moderador no ayuda salvo que la persona se
rinda; si ayuda, la tarea cuenta como no completada.

| # | Tarea | Éxito |
|---|---|---|
| 1 | Inicia sesión con tu correo, tu contraseña y el código de verificación | Llega al panel de la banca |
| 2 | Averigua cuánto dinero tienes en tu cuenta | Dice el saldo correcto |
| 3 | Transfiere S/ 10 a la cuenta que te indico | Llega al comprobante de la transferencia |
| 4 | Intenta transferir más de lo que tienes y cuéntame qué pasa | Entiende que el saldo es insuficiente y cuál es el disponible (escenario 3 de HU-17) |
| 5 | Congela tu tarjeta de débito | La tarjeta queda congelada y el lector lo anuncia |
| 6 | Cierra la sesión | Vuelve a la portada |

## Qué se mide

- **Éxito** por tarea (sí/no) y **tiempo** por tarea.
- **Errores**: cada vez que la persona se pierde, activa algo que no quería o el lector no anuncia un cambio.
- **Satisfacción** al terminar: una pregunta de 1 a 5 («¿qué tan fácil te resultó operar solo?»). Si en el
  proyecto final se aplica el cuestionario SUS previsto en `reporte-usabilidad.md`, se usa también aquí para
  que los resultados se puedan comparar.
- **Comentarios** literales de la persona.

Criterio de aceptación propuesto: todas las tareas completadas por al menos el 80 % de los participantes, y
ningún bloqueo (algo imposible de hacer con el lector).

## Plantilla de resultados (rellenar tras la prueba)

| Participante | Lector y dispositivo | T1 | T2 | T3 | T4 | T5 | T6 | Satisfacción (1-5) | Comentarios |
|---|---|---|---|---|---|---|---|---|---|
| P1 | | | | | | | | | |
| P2 | | | | | | | | | |
| P3 | | | | | | | | | |

**Hallazgos** (cada uno con la pantalla, qué pasó, cómo lo anunció el lector y su gravedad):

-

**Acciones derivadas** (cada hallazgo grave se convierte en una tarea de Jira enlazada a HU-17):

-

## Resultado: simulación automatizada (27-nov-2026)

Por decisión del equipo, la prueba se hizo **simulada y automatizada, sin personas**. No sustituye a una sesión
con usuarios reales de lector de pantalla; da evidencia repetible, que corre en el pipeline en cada cambio.

**Lector de pantalla virtual** (`@guidepup/virtual-screen-reader`, en
`web/ayni-web/src/app/banca/transferir/lector-de-pantalla.test.tsx`). Recorre la transferencia y comprueba lo que
anunciaría un lector:

- cada campo con su etiqueta y el botón «Continuar»;
- el error al confirmar como alerta, con lo que hay que hacer;
- el saldo insuficiente (escenario 3 de HU-17): «Saldo insuficiente: tienes S/ 50.00 disponibles. Escribe un
  importe igual o menor».

**Usuarios simulados** (`web/ayni-web/e2e/usuarios-simulados.a11y.spec.ts`). Hacen las seis tareas **solo con
teclado**, sin ratón, encontrando cada control por su rol y su nombre accesible, que es lo que anuncia un lector.
Corre contra la aplicación construida, con la API simulada.

| Tarea | Éxito | Pulsaciones de Tab | Observación |
|---|---|---|---|
| T1 Iniciar sesión (correo, contraseña, código) | Sí | 5 | Llega a las casillas del código de seis dígitos |
| T2 Consultar el saldo | Sí | 0 | El saldo se lee en la página al entrar |
| T3 Transferir S/ 150 con segundo factor | Sí | 16 | Llega al comprobante «Transferencia realizada» |
| T4 Transferir más de lo que hay | Sí | 13 | La alerta dice el saldo disponible y cómo corregirlo |
| T5 Congelar la tarjeta | Sí | 6 | La tarjeta queda congelada |
| T6 Cerrar sesión | Sí | 10 | Sale de la banca |

**6 de 6 tareas completadas** solo con teclado, sin bloqueos. El criterio de aceptación del protocolo (80 % de
tareas completadas y ningún bloqueo) se cumple en la simulación.

**Límites de la simulación.**

- No mide la satisfacción ni la comprensión real de una persona.
- El lector virtual aproxima lo que diría NVDA o VoiceOver; no es su voz exacta.
- La API es simulada: lo que se mide es la interfaz, no el backend.

Si se hace la sesión con personas, sus resultados van en la plantilla de arriba.
