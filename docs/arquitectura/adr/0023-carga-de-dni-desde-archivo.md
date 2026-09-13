# ADR-0023 · Carga de DNI desde archivo como alternativa a la cámara

- **Estado:** aceptado
- **Fecha:** 12 de septiembre de 2026
- **Historias afectadas:** HU-02 (verificación de identidad)
- **Reemplaza a:** ninguno (extiende [ADR-0022](0022-captura-de-dni-con-guia-visual-de-encuadre.md))

## Contexto

La subtarea 13 de AYNI-13 la pidió el docente de forma explícita (`sprint-backlog/sprint-2.md`),
para tres situaciones que no son la misma:

1. Un equipo sin cámara.
2. Una cámara insuficiente — el OCR (subtareas 3-6) no alcanza su umbral de confianza con esa
   calidad de imagen.
3. Alguien que ya conserva un escaneo de su documento.

El tercer caso es el que importa distinguir: no depende de que la cámara falle. Tratar «subir
un archivo» como un simple mensaje de error de `CapturaDeDocumento` (ADR-0022, subtarea 12) —
visible solo cuando `getUserMedia` rechaza — dejaría fuera a quien tiene cámara que funciona
pero prefiere no usarla. El propio sprint backlog lo ata además a un compromiso del SLA:
**WCAG 2.1 AA sobre más de una forma de completar una tarea.**

## Decisión

**Un `medio` de captura (`"camara" | "archivo"`) que la persona puede cambiar en cualquier
momento**, no solo cuando la cámara falla. El enlace «Subir un archivo en su lugar» está
visible junto a la vista de cámara en vivo, junto al mensaje de «sin cámara», y en cualquier
otro estado que no sea la revisión de una foto ya tomada — con su contrario, «Usar la cámara
en su lugar», disponible simétricamente desde la vista de archivo.

**Reutiliza la máquina de estados de revisión de ADR-0022 en vez de duplicarla.** Tanto la
foto de cámara como el archivo elegido terminan en la misma forma —
`{ blob, url, extension }` — así que la fase `"revisando"` (con «Usar esta foto»/reintentar) y
`usarEstaImagen` sirven para las dos vías sin ramas paralelas. Lo único que cambia entre
medios es cómo se llega hasta ahí y el texto del botón de reintentar («Volver a tomar» vs.
«Elegir otro archivo»).

**Validación de tipo y tamaño en el cliente, contra lo que el backend ya exige.**
`SolicitudDeUrlDeSubidaDto` (subtarea 7) ya limita la extensión a `jpg|jpeg|png` con
`@Pattern`; el selector de archivo valida lo mismo antes de llamar al backend, para que el
rechazo aparezca al instante y no como un 400 después de un viaje de ida y vuelta. El límite
de tamaño (**10 MB**) no está en ninguna parte del diseño — es un tope de sentido común para
una foto de documento, no un requisito de negocio, y se documenta así aquí para que quede
claro que es una elección de implementación, no una cifra del SLA.

**La cámara SÍ se detiene al cambiar a archivo.** A diferencia de «Volver a tomar» dentro del
mismo medio (ADR-0022, que mantiene el `stream` vivo para no repetir el diálogo de permiso),
cambiar de medio es una decisión explícita de la persona de no usar la cámara — no hay razón
para mantenerla encendida de fondo. Volver a «cámara» desde «archivo» sí vuelve a pedir
permiso: es un nuevo intento, no un reintento del mismo.

**La extensión del archivo subido no se fuerza a `.jpg`.** La foto de cámara siempre produce
JPEG (`canvas.toBlob(..., "image/jpeg")`), pero un archivo puede ser PNG. `foto.extension` se
guarda junto al blob y viaja hasta `solicitarUrlDeSubida`, en vez de asumir `"jpg"` como hacía
`CapturaDeDocumento` antes de esta subtarea.

## Alternativas evaluadas

**Aceptar cualquier tipo de imagen y convertirlo a JPEG en el cliente antes de subir (como ya
hace la cámara).** Se descartó: el backend ya acepta PNG directamente
(`SolicitudDeUrlDeSubidaDto`, subtarea 7) y el pipeline de Python lee imágenes con OpenCV, que
soporta PNG sin conversión. Convertir de todos modos sería trabajo sin ningún beneficio real,
y con un costo real: una reconversión pierde calidad sobre una imagen que ya viene en su mejor
forma (un escaneo).

**Mostrar «subir archivo» solo como mensaje de error de la cámara**, que era el diseño de la
subtarea 12 antes de esta ADR. Descartado explícitamente por el motivo ya dicho: deja fuera al
tercer caso del docente (quien ya tiene un escaneo).

## Consecuencias

**A favor**

- Cumple los tres casos que pidió el docente, no solo el de «la cámara falló».
- Cero duplicación de la máquina de estados de revisión/subida entre cámara y archivo.
- Probado con Vitest: tipo inválido, tamaño excedido, subida con la extensión correcta según
  el archivo, y cambio de medio en ambas direcciones (8 casos nuevos, 14 en total en el
  fichero).

**En contra**

- El límite de 10 MB es una heurística sin respaldo en el diseño, igual que otros umbrales ya
  señalados en ADR-0012/0013 — puede necesitar ajuste si en la práctica los escaneos de alta
  resolución lo superan con frecuencia.
- Sigue sin existir el mensaje específico de calidad (ADR-0022, pendiente de
  `POST /kyc/verify`): un archivo subido con reflejo o borroso falla igual de genérico que una
  foto de cámara con el mismo problema.

## Pendiente

- Mismo pendiente que ADR-0022: enlazar el flujo real desde el registro, y el mensaje
  específico de calidad cuando exista `POST /kyc/verify`.
- Calibrar el límite de 10 MB si aparece evidencia de que es demasiado bajo o demasiado alto.
