# ADR-0012 · Detección de que la imagen es un DNI por geometría, no por clasificador entrenado

- **Estado:** aceptado
- **Fecha:** 6 de septiembre de 2026
- **Historias afectadas:** HU-02 (verificación de identidad)
- **Reemplaza a:** ninguno

## Contexto

La subtarea 3 de AYNI-13 pide detectar si una imagen subida por el usuario corresponde a un
DNI peruano, antes de invertir tiempo de cómputo en el OCR (subtarea 5) o en el cotejo facial
(HU-03). El sprint backlog solo nombra la subtarea; no existe ADR previo, dataset de DNI de
control, ni criterio técnico documentado de qué características del documento se validan.

`DetectorDocumentoPort.es_documento_identidad(clave_objeto: str) -> bool`
(`src/domain/port/verificador_identidad.py`) ya define el contrato: recibe la clave del objeto
en MinIO y responde solo con un booleano.

## Decisión

**Se detecta por geometría del contorno, con OpenCV, no con un clasificador de imágenes
entrenado ni con detección de texto.**

El pipeline (`DetectorDocumentoOpenCV`, en `infrastructure/vision/`):

1. Convierte a escala de grises y detecta bordes (Canny) sobre la imagen decodificada.
2. Encuentra el contorno de mayor área y lo aproxima a un polígono.
3. Si el polígono no tiene 4 vértices, no es un documento reconocible → `False`.
4. Ordena los 4 puntos y aplica una transformación de perspectiva (four-point transform) para
   enderezar el documento, igual que un escáner de documentos.
5. Compara la proporción ancho/alto resultante contra el formato ISO/IEC 7810 ID-1
   (ratio ≈ 1.586, el mismo que usa el DNI peruano), con una tolerancia de ±0.15.

La imagen enderezada que produce este pipeline es la base que reutilizarán la subtarea 4
(validación de calidad: nitidez, reflejos, encuadre) y la subtarea 5 (OCR con PaddleOCR sobre
la imagen ya recortada y de frente), evitando repetir la detección de contorno en cada etapa.

## Alternativas evaluadas

**Clasificador de imágenes entrenado (CNN).** Es lo que usan soluciones KYC comerciales, pero
exige un dataset etiquetado de DNI peruanos (positivos y negativos) que no existe en este
proyecto, y el costo de entrenar, versionar y mantener un modelo no se justifica para esta
fase — es un proyecto académico con foco en el flujo completo, no en precisión de visión por
computador de nivel productivo.

**Detección de texto clave (p. ej. "REPÚBLICA DEL PERÚ", "RENIEC") con el módulo de detección
de PaddleOCR.** Más específico al DNI peruano que solo geometría, pero duplicaría trabajo con
la subtarea 5 (que ya hace OCR completo sobre la misma imagen) y añadiría latencia de un motor
de OCR pesado solo para responder un booleano. Se descarta para esta subtarea; si la geometría
sola resulta insuficiente en producción, se puede añadir como una segunda señal más adelante.

**Solo verificar dimensiones de la imagen completa (sin buscar contorno).** Más simple, pero
asume que el usuario encuadra el documento ocupando toda la foto, sin fondo. En la práctica
(mesa, mano sosteniendo el documento) esto casi nunca ocurre; se descarta.

## Consecuencias

**A favor**

- No requiere dataset ni entrenamiento: funciona desde el primer commit.
- Es rápido (milisegundos), adecuado para ejecutarse antes del OCR pesado.
- El enderezado de perspectiva es un subproducto reutilizable por las subtareas 4 y 5, no solo
  un efecto colateral de esta detección.

**En contra**

- Es una heurística, no un clasificador: una tarjeta de crédito o cualquier documento con la
  misma proporción ISO/IEC 7810 ID-1 (que además es el formato estándar de muchas tarjetas de
  identidad y de pago) pasaría esta detección. Se acepta porque el objetivo de esta subtarea es
  descartar fotos claramente inválidas (una selfie, una pared, un documento con las esquinas
  cortadas) antes del OCR, no sustituir la validación de contenido que hará la subtarea 5 y 6
  (dígito verificador del DNI) más adelante.
- La tolerancia de ±0.15 en la proporción es un valor inicial sin medir contra imágenes reales
  (no hay conjunto de control para HU-02, a diferencia de HU-03 que sí lo prevé en su sprint
  backlog). Puede requerir ajuste una vez haya evidencia empírica.

## Pendiente

- Medir la tasa de falsos positivos/negativos de esta heurística contra fotos reales de DNI
  cuando exista un conjunto de control (ver riesgo R-01/R-02 en `sprint-2.md`).
- Evaluar si conviene añadir la señal de texto clave (PaddleOCR) como refuerzo, si la geometría
  sola no alcanza la precisión requerida en producción.
