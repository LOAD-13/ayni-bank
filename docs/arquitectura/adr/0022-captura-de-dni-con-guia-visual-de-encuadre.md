# ADR-0022 · Captura de DNI con guía visual de encuadre

- **Estado:** aceptado
- **Fecha:** 12 de septiembre de 2026
- **Historias afectadas:** HU-02 (verificación de identidad)
- **Reemplaza a:** ninguno

## Contexto

La subtarea 12 de AYNI-13 pide la pantalla de captura de DNI con guía visual de encuadre —
paso 2 («DNI anverso») y paso 3 («DNI reverso») del onboarding de cinco pasos, ya fijados en
`IndicadorDeProgreso.tsx` desde HU-01.

El backend de esta pantalla ya existía antes de esta subtarea, construido pero sin UI que lo
use: `DocumentoKycController` (`POST /api/v1/solicitudes/{id}/documentos/url-de-subida`,
subtarea 7) y las funciones `solicitarUrlDeSubida`/`subirDocumento` en `lib/api.ts`. El
ADR-0017 lo anotó explícitamente: *"Las pantallas reales de captura de DNI (cámara, guía
visual, carga desde archivo) son las subtareas 12 y 13, todavía no iniciadas"*. Esta subtarea
es la primera mitad de esa promesa.

**Qué NO pide esta subtarea, aunque el ADR-0013 lo insinuara.** ADR-0013 (subtarea 4) dejó
`ResultadoValidacionCalidad` con el detalle de qué falló (nitidez/reflejos/encuadre)
"*para que la subtarea 12 pueda mostrar un mensaje específico*" — en condicional, no como
requisito cerrado. Ese detalle solo puede llegar a esta pantalla a través de
`POST /kyc/verify` en `ayni-kyc-service`, que **todavía no existe** (el `main.py` de Python
solo expone `/health` y `/metrics` — deuda ya anotada en el plan de AYNI-13). Pedirle a esta
subtarea que muestre "hay un reflejo" sería construir sobre un endpoint que no está. Lo que
sí puede resolver sin depender de nada más es la guía ANTES de la foto: un marco geométrico
que ayuda a encuadrar bien desde el principio, reduciendo cuántas fotos van a fallar esa
validación cuando el caso de uso de integración exista.

## Decisión

**Un marco guía con la proporción física real del DNI (ISO/IEC 7810 ID-1, 85.60 × 53.98 mm),
superpuesto sobre la vista en vivo de la cámara**, en `CapturaDeDocumento`
(`componentes/kyc/`). No es un rectángulo arbitrario: encuadrar el documento dentro de él es,
literalmente, encuadrarlo con la proporción correcta.

**La transmisión de la cámara no se detiene al tomar la foto.** Se mantiene viva en segundo
plano (oculta con CSS, no desmontada) mientras la persona revisa la captura, y solo se
detiene al confirmar la subida o al salir de la pantalla. La alternativa —parar el `stream`
al fotografiar y volver a pedirlo si la persona pulsa «Volver a tomar»— dispara otra vez el
diálogo de permiso del navegador en cada reintento, lo que en Chrome interrumpe el flujo con
una notificación emergente por cada foto descartada.

**Dos componentes, una frontera de responsabilidad.** `CapturaDeDocumento` no sabe de rutas
ni de navegación: recibe `solicitudId`/`tipoDocumento`/`cara` y avisa con `onCompletado()`
cuando la subida termina. `PasoDeCapturaDeDni` pone la cabecera, el `IndicadorDeProgreso` y
decide a dónde navegar al completarse. Es la misma frontera que ya separaba
`EstadoDeLaCuenta` (qué hacer) de `registro/listo/page.tsx` (cómo se llega y qué cabecera
lleva).

**Sin `solicitudId`, un mensaje, no una pantalla en blanco ni un error de tipos.** Ambas
rutas (`/registro/dni-anverso`, `/registro/dni-reverso`) son navegables directamente, sin que
todavía exista quien las enlace desde el resto del flujo — ver «Pendiente». Igual que
`registro/listo/page.tsx` con `titular` ausente, la ausencia de `solicitudId` se resuelve con
un mensaje y un enlace de vuelta, no con una ruta rota.

**Sin cámara, se dice la verdad en vez de fingir una alternativa que no existe.** La subtarea
13 (carga desde archivo) es la salida real para quien no tiene cámara o le falla el permiso —
y todavía no está construida. El mensaje de "sin cámara" lo anuncia («Pronto podrás subir el
DNI como archivo») sin enlazar a nada que no funcione, mismo criterio que `/pendiente`.

## Alternativas evaluadas

**Pedir el detalle de calidad al backend antes de subir, simulando la respuesta mientras
`POST /kyc/verify` no existe.** Descartado: construir sobre un contrato inventado es
exactamente el tipo de deuda oculta que este proyecto evita documentar como tal en otros
ADR (0017, 0018, 0020). Mejor un marco geométrico honesto hoy que una integración fingida.

**Detener la cámara entre fotos.** Descartado por la experiencia de usuario: repetir el
diálogo de permiso del navegador en cada «Volver a tomar» es fricción que el `stream` vivo
evita sin costo real (una pestaña de cámara encendida de más, liberada al confirmar o salir).

**Convertir la vista previa capturada con `next/image`.** Descartado: `next/image` exige una
URL servible (archivo, `/public`, o un *loader* remoto), y la vista previa es un `Blob` local
(`blob:`) que nunca toca el servidor. Se usa `<img>` con el `no-img-element` desactivado
explícitamente en esa única línea, con el motivo dicho en el propio comentario.

## Consecuencias

**A favor**

- Reutiliza el backend y el cliente HTTP que ya existían (subtarea 7): ningún cambio en
  `DocumentoKycController` ni en `lib/api.ts`.
- Probado con Vitest simulando `getUserMedia`, `canvas.getContext`/`toBlob` y
  `URL.createObjectURL` — la máquina de estados completa (permiso → en vivo → foto tomada →
  subiendo → éxito/error) queda cubierta sin depender de una cámara real, que ningún entorno
  de CI tiene.
- Las dos rutas pasan el barrido de accesibilidad WCAG 2.1 AA (`test:a11y`), tanto con la
  cámara denegada como sin `solicitudId`.

**En contra**

- Nada navega todavía a `/registro/dni-anverso` desde el resto del flujo: el formulario de
  registro (HU-01) termina en «Revisa tu correo» y no siguen definidos ni el enlace de
  verificación de correo ni el caso de uso de integración de HU-02 (deuda ya anotada en el
  plan de AYNI-13, ampliada aquí). Las rutas son alcanzables y funcionales por URL directa,
  mismo patrón ya usado por `registro/listo?titular=...`.
- El mensaje específico de calidad (reflejo/nitidez/encuadre) que ADR-0013 dejó preparado
  sigue sin destino: solo llega cuando exista `POST /kyc/verify`. Hasta entonces, un
  documento que la cámara sube bien pero que el backend (cuando exista) rechace por calidad
  no tiene, en esta pantalla, un mensaje más específico que el genérico de error de subida.

## Pendiente

- Enlazar el flujo real: qué lleva de «Revisa tu correo» (fin de HU-01) a
  `/registro/dni-anverso?solicitudId=...` — probablemente el enlace de verificación de
  correo, fuera del alcance ya delimitado de AYNI-13.
- Cuando exista `POST /kyc/verify`, decidir cómo `CapturaDeDocumento` recibe y muestra el
  motivo específico de rechazo (`ResultadoValidacionCalidad`, ADR-0013) en vez del mensaje
  genérico actual.
- Subtarea 13: carga de DNI desde archivo, la alternativa real para quien no tiene cámara.
