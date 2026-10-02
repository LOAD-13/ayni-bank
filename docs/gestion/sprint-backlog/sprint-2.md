# Sprint Backlog — Sprint 2 · Onboarding KYC

**7 – 20 de septiembre de 2026** · 2 semanas · 5 ítems · 50 puntos

## Sprint Goal

> Que el KYC simulado del Sprint 1 se sustituya por verificación real de identidad: DNI con OCR y
> cotejo biométrico facial, y que nadie llegue a esa verificación sin haber demostrado antes que
> controla el correo o el teléfono que declaró.

## Capacidad

| Ítem | Esfuerzo |
|---|---|
| `AYNI-13` · HU-02 · Verificación de identidad mediante DNI | 50 h |
| `AYNI-14` · HU-03 · Verificación biométrica facial | 44 h |
| `AYNI-124` · HU-22 · Segundo factor a elección en el registro | 33 h |
| `AYNI-122` · T-11 · Validación del JWT en el gateway | 4 h |
| `AYNI-31` · HU-20 · Despliegue continuo con aprobación | 26 h |

**120 h disponibles · comprometido: 157 h.**

> **El sprint está sobrecomprometido y consta.** Es exactamente el riesgo `R-03` de nuestra propia
> matriz —sobredimensionamiento del alcance frente a la capacidad real—, y la regla de alcance del
> Product Backlog exige retirar un ítem equivalente por cada incorporación. No se retira ninguno:
> el Scrum Master asume explícitamente el compromiso de sostenerlo con la incorporación de un
> Developer al equipo. Queda escrito aquí para que la Sprint Review contraste el compromiso contra
> lo entregado, y no para justificarlo después.
>
> **Orden de sacrificio si la capacidad no alcanza,** decidido por anticipado y no en caliente:
> primero cae `AYNI-31` —el despliegue a producción no bloquea ninguna funcionalidad—, después la
> prueba de vivacidad de `AYNI-14` degrada a detección básica según su propio plan de riesgo. `HU-02`
> y `HU-22` no se tocan: son la meta del sprint.

---

### `AYNI-13` · HU-02 · Verificación de identidad mediante DNI [13 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Definir el contrato OpenAPI de `kyc-service` en `contracts/` | 3h |
| 2 | Generar el cliente Java desde el contrato en tiempo de build | 2h |
| 3 | Implementar la detección de que la imagen es un DNI peruano | 6h |
| 4 | Implementar la validación de calidad: nitidez, reflejos y encuadre | 4h |
| 5 | Implementar el OCR de anverso y reverso con PaddleOCR | 6h |
| 6 | Validar el dígito verificador del número de DNI | 1h |
| 7 | Implementar la subida a MinIO con URL pre-firmada desde el navegador | 4h |
| 8 | Calcular y almacenar el hash SHA-256 de cada documento | 2h |
| 9 | Migración Flyway de `solicitud_onboarding` y `documento_kyc` | 2h |
| 10 | Envolver la llamada a `kyc-service` con Resilience4j | 3h |
| 11 | Implementar el límite de tres intentos y la derivación a revisión manual | 3h |
| 12 | Pantalla de captura de DNI con guía visual de encuadre | 6h |
| 13 | Carga del DNI desde archivo como alternativa a la cámara | 4h |
| 14 | Feature de Cucumber con los cinco escenarios | 4h |

**Sobre la subtarea 13.** La pidió el docente de forma explícita, planteando el caso de un equipo con
cámara de mala calidad —con la que el OCR no alcanza su umbral de confianza— o directamente sin
cámara.

No es un extra: depender de una sola vía de entrada deja fuera a los equipos de escritorio sin
cámara, a quien tiene una cámara insuficiente y a quien ya conserva un escaneo de su documento.
Además incumpliría el criterio de **WCAG 2.1 AA sobre múltiples formas de completar una tarea**, que
el proyecto comprometió en su SLA.

El botón de subida va **junto a la captura y con el mismo peso visual**, no escondido tras un enlace
secundario. Ambas vías terminan en MinIO con URL pre-firmada y el `kyc-service` recibe únicamente la
clave del objeto: para el OCR, una foto tomada y un archivo subido son la misma entrada. El tipo de
archivo se valida por contenido y no por extensión, porque un `.jpg` renombrado no es una imagen.

Esto sube `AYNI-13` de 46 h a **50 h**, y el sprint de 99 h a **103 h** sobre 120 disponibles. Sigue
cabiendo.

### `AYNI-14` · HU-03 · Verificación biométrica facial [13 pts]

| # | Subtarea | Est. |
|---|---|---|
| 1 | Implementar la detección de rostro único en la selfie | 4h |
| 2 | Implementar la prueba de vivacidad contra fotografías y pantallas | 8h |
| 3 | Implementar el cotejo facial contra la foto del DNI | 6h |
| 4 | Hacer configurable el umbral de similitud sin desplegar | 2h |
| 5 | Registrar score y decisión en la pista de auditoría | 2h |
| 6 | Implementar la pantalla de consentimiento informado (Ley 29733) | 3h |
| 7 | Bloquear la captura si no hay consentimiento otorgado | 2h |
| 8 | Derivar a revisión manual del Oficial de Cumplimiento bajo el umbral | 3h |
| 9 | Preparar el conjunto de imágenes de control para medir precisión | 4h |
| 10 | Pantalla de captura de selfie con detección en vivo | 6h |
| 11 | Feature de Cucumber con los cuatro escenarios | 4h |

### `AYNI-124` · HU-22 · Segundo factor a elección y verificación del contacto [8 pts]

| # | Subtarea | Clave | Est. |
|---|---|---|---|
| 1 | Modelar `MetodoDeSegundoFactor` y `DesafioPorCodigo` en el dominio | `AYNI-125` | 2h |
| 2 | Migración Flyway de `metodo_segundo_factor` y `desafio_por_codigo` | `AYNI-126` | 2h |
| 3 | Endpoint de elección y alta del método | `AYNI-127` | 3h |
| 4 | Código de un solo uso por correo: generación, hash, caducidad y tres intentos | `AYNI-128` | 3h |
| 5 | Adaptador de envío de correo con proveedor de nivel gratuito | `AYNI-129` | 3h |
| 6 | Impedir el avance al KYC hasta que el segundo factor esté verificado | `AYNI-130` | 2h |
| 7 | Extender el ingreso para aceptar el método dado de alta y permitir cambiarlo | `AYNI-131` | 3h |
| 8 | Pantalla de elección del método de verificación, paso 2 de 6 | `AYNI-132` | 4h |
| 9 | Pantalla de alta de la app autenticadora con QR y clave manual | `AYNI-133` | 3h |
| 10 | Pantalla de código por correo con cuenta atrás y reenvío | `AYNI-134` | 3h |
| 11 | Actualizar el indicador de progreso del onboarding de 5 a 6 pasos | `AYNI-135` | 2h |
| 12 | Feature de Cucumber con los cinco escenarios | `AYNI-136` | 3h |

**Por qué esta historia va antes del OCR y no después.** Lo planteó el docente en la revisión de la
semana 4: un DNI se roba, se presta o se fotografía, así que el documento por sí solo no prueba
identidad. Lo que sí es difícil de suplantar es la **posesión** del segundo factor. Al exigirla
antes de la captura, un registro fraudulento se detiene sin haber subido una imagen, sin haber
gastado OCR y —lo que más importa— **sin haber tratado un solo dato biométrico**, que la Ley N.º
29733 clasifica como dato sensible.

**El SMS queda fuera.** Se diseña la pantalla y se muestra como «Próximamente» con su motivo
visible, porque el envío es de pago. Candidato a los sprints de holgura, sin compromiso de fecha.

### `AYNI-122` · T-11 · Validación del JWT en el gateway [3 pts · 4h]

Reclasificación de `AYNI-83`. Retira dos provisionalidades del Sprint 1: el titular que hoy viaja
por la URL en lugar de salir del token, y la confianza implícita en la red interna del compose.

### `AYNI-31` · HU-20 · [NF] Despliegue continuo con aprobación [13 pts · 26h]

Arrastrada del Sprint 1. Sin ella no hay entorno desplegado, y sin entorno desplegado la medición
de Core Web Vitals sigue siendo de laboratorio y no de campo.

---

## Riesgos del sprint

| Riesgo | Mitigación |
|---|---|
| La precisión del OCR y del cotejo no alcanza el umbral (R-01, R-02) | Conjunto de imágenes de control desde el primer día; umbrales configurables; flujo manual operativo |
| La prueba de vivacidad es el trabajo más incierto del proyecto | Se aborda primero; si consume el sprint, se degrada a detección básica y se refuerza en el Sprint 8 |

## Definition of Done

Aplica la de [`DEFINITION_OF_DONE.md`](../../../DEFINITION_OF_DONE.md).

**Adicional:** medición documentada de la tasa de acierto sobre el conjunto de control. Sin cifra
medida, la historia no está terminada.
