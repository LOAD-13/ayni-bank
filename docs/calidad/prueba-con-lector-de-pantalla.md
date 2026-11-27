# Prueba con usuarios reales usando lector de pantalla — protocolo

**Historia:** HU-17 · AYNI-294 · Estado: **protocolo listo, prueba pendiente de ejecutar**

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
