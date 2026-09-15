# ADR-0013 · Validación de nitidez, reflejos y encuadre con métricas clásicas de OpenCV

- **Estado:** aceptado
- **Fecha:** 6 de septiembre de 2026
- **Historias afectadas:** HU-02 (verificación de identidad)
- **Reemplaza a:** ninguno (complementa [ADR-0012](0012-deteccion-de-dni-por-geometria-con-opencv.md))

## Contexto

La subtarea 4 de AYNI-13 pide validar la calidad de la foto de un DNI (nitidez, reflejos,
encuadre) antes de pasarla al OCR (subtarea 5). Igual que en la subtarea 3, no existe
especificación técnica previa de qué umbrales o métricas usar, ni conjunto de control de
imágenes reales para calibrarlas.

El ADR-0012 ya prometía que esta subtarea reutilizaría el pipeline de detección de contorno de
la subtarea 3 en lugar de repetirlo. Cumplir esa promesa exigía extraer ese pipeline —hasta
ahora privado dentro de `DetectorDocumentoOpenCV`— a un módulo compartido.

## Decisión

**Se extrae el pipeline de "encontrar y enderezar documento" a
`infrastructure/vision/procesamiento_documento.py`**, con una función `procesar_documento`
que retorna `DocumentoProcesado` (imagen original, contorno en coordenadas originales, e
imagen enderezada). `DetectorDocumentoOpenCV` (subtarea 3) se refactorizó para consumir este
módulo en vez de duplicar la lógica.

**Se valida calidad con tres métricas clásicas de visión por computador, sin modelo entrenado:**

1. **Nitidez** — varianza del Laplaciano sobre la imagen enderezada en escala de grises
   (`cv2.Laplacian(...).var()`). Es la métrica estándar de facto para detectar blur: una imagen
   nítida tiene bordes marcados (varianza alta); una borrosa, bordes suavizados (varianza baja).
   Umbral inicial: **100.0**.
2. **Reflejos** — fracción de píxeles saturados (brillo > 240 sobre 255) en la imagen
   enderezada. Un reflejo de flash sobre el plástico laminado del DNI produce una zona de
   brillo uniforme cercano al blanco puro. Umbral inicial: **máximo 5% del área**.
3. **Encuadre** — dos condiciones sobre el contorno detectado en la subtarea 3, en coordenadas
   de la foto original (no de la imagen ya recortada): (a) ningún vértice del contorno debe
   caer a menos del 2% del ancho/alto del borde de la foto (indicaría documento cortado), y
   (b) el área del contorno debe ocupar entre 15% y 95% del área total de la foto (ni
   demasiado lejos ni ocupando el cuadro sin margen).

**El resultado interno distingue cuál de los tres controles falló**
(`ResultadoValidacionCalidad`: `es_nitida`, `sin_reflejos`, `bien_encuadrada`, más la propiedad
`es_valida`), aunque el contrato OpenAPI solo exponga el motivo genérico
`QUALITY_CHECK_FAILED`. La granularidad interna sirve para logs de diagnóstico y para que la
subtarea 12 (pantalla de captura con guía visual) pueda mostrar un mensaje específico
("hay un reflejo", "acerca el documento") en vez de un rechazo genérico.

## Alternativas evaluadas

**Un modelo de ML para calidad de imagen (blur/glare detection entrenado).** Mismo argumento
que en el ADR-0012: no hay dataset, y el costo de entrenar y mantener un modelo no se justifica
en esta fase del proyecto.

**No distinguir el control que falló, solo un booleano genérico.** Más simple, pero pierde
información útil sin costo real de mantenerla — la subtarea 12 (guía visual al usuario)
probablemente la necesite, y es más barato modelarla ahora que añadirla después.

**Aplicar los tres controles sobre la imagen original en vez de la enderezada.** Se descartó:
el encuadre necesita la foto original (para saber si el documento toca los bordes del
*frame*), pero nitidez y reflejos deben medirse sobre el documento ya recortado — de lo
contrario el fondo de la foto (mesa, mano, iluminación ambiental) contamina la métrica. Por
eso `_bien_encuadrada` recibe el `DocumentoProcesado` completo, mientras que `_es_nitida` y
`_sin_reflejos` reciben solo `imagen_enderezada`.

## Consecuencias

**A favor**

- No requiere dataset ni entrenamiento.
- Reutiliza el contorno ya calculado en la subtarea 3 (una sola detección de bordes por foto,
  no una por cada etapa).
- El resultado granular deja trazabilidad de por qué se rechazó una foto, útil para soporte y
  para la subtarea 12.

**En contra**

- Los tres umbrales (100.0, 5%, 2%/15%-95%) son heurísticas iniciales sin validar contra fotos
  reales de DNI — mismo riesgo ya señalado en el ADR-0012 (R-01/R-02 de `sprint-2.md`).
  Requieren calibración cuando exista un conjunto de control.
- La métrica de nitidez (Laplaciano) es sensible al contenido de la imagen: un documento con
  poco texto o de fondo muy uniforme puede dar varianza baja sin estar borroso. Se acepta como
  limitación conocida; el DNI real tiene suficiente texto e iconografía para que esto no sea
  un problema práctico.

## Pendiente

- Calibrar los tres umbrales contra fotos reales de DNI cuando exista un conjunto de control
  (mismo pendiente que el ADR-0012, ampliado a estos tres valores).
