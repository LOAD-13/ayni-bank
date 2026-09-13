# ADR-0024 · Feature Cucumber de verificación de identidad: solo 2 de 5 escenarios

- **Estado:** aceptado
- **Fecha:** 12 de septiembre de 2026
- **Historias afectadas:** HU-02 (verificación de identidad)
- **Reemplaza a:** ninguno

## Contexto

La subtarea 14 de AYNI-13 pide "feature Cucumber con 5 escenarios". Antes de escribir nada
se trajo el texto real de AYNI-13 desde Jira (no se iban a inventar los escenarios), y ahí
apareció el problema: los cinco escenarios que Jira aprobó describen un flujo de extremo a
extremo — detectar que la imagen es un DNI, validar calidad, extraer datos por OCR,
mostrarlos para confirmación, almacenar en MinIO — que cruza `identity-service` (Java) y
`kyc-service` (Python) a través de un caso de uso de integración que **todavía no existe**.
Ese caso de uso es deuda explícita desde la subtarea 9 (ADR-0019), ampliada en la 10
(ADR-0020) y la 11 (ADR-0021): nadie llama todavía a `VerificadorKycPort.iniciar`, no hay
endpoint `POST /kyc/verify` en Python, y no hay repositorio para `documento_kyc`.

`registro-de-usuario.feature` (HU-01) fija una regla explícita en su propia cabecera: los
escenarios se copian de Jira línea a línea **para que, cuando la historia y el código dejan
de coincidir, la prueba falle** — no para que se finjan o se dejen "pendientes". Escribir los
cinco escenarios de HU-02 sin poder automatizarlos de verdad rompería esa regla en el primer
intento de esta historia.

**Cuánto costaría cerrar la integración completa, medido antes de decidir**: se estimó en
16-25 horas repartidas en tres partes — el endpoint Python que hoy no existe (6-10h), la
orquestación Java completa incluida la persistencia de `documento_kyc` y el criterio de
coincidencia OCR↔declarado que ADR-0009 dejó sin decidir (6-9h), y una pantalla de
corrección de datos OCR que **ninguna de las 14 subtareas de AYNI-13 cubre** (4-6h). Es más
grande que las subtareas 10-13 juntas. El Product Owner decidió no absorber ese alcance en
la subtarea 14.

## Decisión

**Se automatizan solo 2 de los 5 escenarios: "Agotamiento de intentos" y "El servicio de
visión no responde".** Son los únicos que describen una reacción de `identity-service` a un
resultado ya conocido (fallo repetido, servicio caído) sin necesitar la integración con
Python. Los otros tres —captura correcta, documento que no es un DNI, calidad
insuficiente— no se escriben en el feature hasta que exista la integración: incluirlos sin
poder automatizarlos de verdad habría sido precisamente lo que la regla de HU-01 prohíbe.

Los dos escenarios automatizados se copian de Jira con **tres diferencias, documentadas en
la cabecera del feature en vez de ocultarse**:

1. Jira llama al estado `EN_REVISION`; el sistema persiste `EN_REVISION_MANUAL`. Los pasos
   comprueban el nombre real.
2. Jira describe "3 capturas del mismo lado"; el contador de ADR-0021 es uno por solicitud,
   no uno por anverso y otro por reverso.
3. **La más importante, encontrada al ejecutar el escenario contra el código real** (la
   prueba cumpliendo exactamente la función para la que existe): Jira narra "falla 3 veces,
   intenta una cuarta, y esa cuarta deriva". ADR-0021 implementó que el **tercer** fallo
   mismo deriva — no hay un cuarto intento que llegue a procesarse. Se decidió no tocar
   ahora una lógica ya enviada, probada y documentada, por una ambigüedad del propio texto
   de Jira: su criterio de aceptación en prosa dice *"máximo 3 intentos... antes de
   derivar"*, que coincide con el código, no con el conteo de la narrativa del escenario.

**Se añadió el aviso al solicitante que faltaba.** Los escenarios 4 y 5 de Jira piden
explícitamente que el sistema notifique/informe al solicitante, y `GestionarFalloDeVerificacionKycService`
(subtarea 11) no llamaba a ningún notificador — nada lo hacía. Se creó
`NotificadorDeVerificacionKycPort` (con la misma implementación provisional basada en logs
que `NotificadorDeSeguridadPort`, hasta que `ayni-notification-service` exista con el outbox
real, ADR-0003) y se conectó al servicio. Es una pieza pequeña y mecánica —mismo patrón que
ya existía dos veces en el código— así que se construyó en vez de recortar esa cláusula del
escenario o fingir que ya pasaba.

## Alternativas evaluadas

**Escribir los 5 escenarios y marcar 3 como `@pendiente`/`PendingException`.** Cucumber lo
soporta, pero contradice el motivo de ser de esta práctica en el proyecto: la regla no es
"todo escenario de Jira debe existir en el feature", es "todo escenario que existe en el
feature prueba código real". Un escenario pendiente no miente, pero tampoco prueba nada, y
deja la falsa sensación de cobertura completa con solo mirar el nombre de la característica.

**Rediseñar el límite de intentos para que derive en el cuarto fallo, no en el tercero,**
para que coincida literalmente con la narrativa del escenario de Jira. Descartado: cambiaría
comportamiento ya enviado (subtarea 11, commit `79eb70f`), ya documentado (ADR-0021) y ya
probado, apoyado en una ambigüedad del propio ticket (su prosa y su ejemplo no coinciden
entre sí). El criterio de aceptación en prosa —lo más autoritativo del ticket— respalda el
código tal como está.

## Consecuencias

**A favor**

- Los 2 escenarios automatizados prueban código real: si alguien cambia
  `LIMITE_DE_INTENTOS`, el nombre del estado, o deja de notificar, esta prueba lo nota.
- El aviso al solicitante, que faltaba desde la subtarea 11, ya existe y está probado (tanto
  a nivel de unidad como de aceptación).
- Ninguna promesa falsa: el feature no afirma cubrir HU-02 completa.

**En contra**

- Solo 2 de los 5 criterios de aceptación de AYNI-13 tienen prueba de aceptación. Es una
  entrega parcial de la subtarea 14 tal como Jira la redactó, decidida explícitamente con el
  Product Owner en vez de descubierta en la revisión de sprint.
- La discrepancia de conteo (tercer fallo vs. cuarto intento) queda documentada pero no
  resuelta: si en el futuro se decide que Jira tenía razón y hace falta un intento
  "gratis" adicional, hay que revisar ADR-0021, la migración V6 (el `CHECK` acepta hasta 3;
  permitir un cuarto fallo registrado exigiría subirlo) y esta prueba.

## Pendiente

- Los 3 escenarios no automatizados (captura correcta, documento que no es un DNI, calidad
  insuficiente) quedan para cuando exista el caso de uso de integración completo — ver la
  estimación de 16-25h ya conversada con el Product Owner, repartida en: endpoint Python
  (`POST /kyc/verify` real sobre las subtareas 3-6 ya construidas), orquestación Java
  (`documento_kyc` persistido, `getVerificationResult` envuelto con resiliencia, criterio de
  coincidencia OCR↔declarado que ADR-0009 dejó sin decidir) y una pantalla de corrección de
  datos OCR que ninguna subtarea de AYNI-13 cubre hoy.
- Si se decide que el límite de intentos debe permitir un intento adicional "gratis" antes
  de derivar (para calzar con la narrativa del escenario de Jira), revisar ADR-0021 y la
  migración V6.
