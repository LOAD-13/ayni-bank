# ADR-0014 · `opencv-contrib-python` en vez de `opencv-python-headless`

- **Estado:** aceptado
- **Fecha:** 6 de septiembre de 2026
- **Historias afectadas:** HU-02 (verificación de identidad)
- **Reemplaza a:** la elección de `opencv-python-headless==4.14.0.94` hecha en la subtarea 3
  (documentada implícitamente en [ADR-0012](0012-deteccion-de-dni-por-geometria-con-opencv.md),
  sin ADR propio en su momento)

## Contexto

Las subtareas 3 y 4 de AYNI-13 fijaron `opencv-python-headless==4.14.0.94`: sin dependencias de
GUI/X11, correcto para un contenedor servidor. Al implementar la subtarea 5 (OCR con
PaddleOCR), se descubrió que `paddleocr` no es compatible con esa elección.

`paddleocr` (2.x y 3.x) depende, a través de `paddlex[ocr-core]`, de **`opencv-contrib-python`
fijado con `==`** (no un rango): `paddlex[ocr-core] 3.7.x` exige exactamente
`opencv-contrib-python==4.10.0.84`. No hay forma de declarar `opencv-python-headless` en su
lugar y que pip lo acepte como sustituto: son paquetes distintos en PyPI que ambos instalan en
el mismo espacio de nombres `cv2/`, y pip no los trata como intercambiables.

Se probó declarar `opencv-contrib-python-headless` (variante headless del mismo paquete que
paddlex exige) y también fijar una versión propia distinta a la de paddlex: en ambos casos, el
resultado fue **dos o tres paquetes de opencv instalados simultáneamente**, y cuál de ellos
"gana" (queda con sus archivos vigentes en `cv2/__init__.py`) depende del orden de instalación
de pip, no es determinístico, y puede cambiar entre builds.

## Decisión

**Se usa `opencv-contrib-python==4.10.0.84` (sin headless), exactamente la versión que exige
`paddlex[ocr-core]`.** Es la única combinación que resuelve a un solo paquete de opencv
instalado, de forma reproducible.

Se verificó que el código de las subtareas 3 y 4 (`procesamiento_documento.py`,
`detector_documento_opencv.py`, `validador_calidad_opencv.py`) sigue funcionando sin cambios
contra esta versión: los 8 tests existentes pasan, y `mypy --strict` no reporta errores nuevos.

Como `opencv-contrib-python` no es headless, requiere librerías de sistema adicionales para
importarse en Linux. Se añaden `libsm6`, `libxext6` y `libxrender1` al `Dockerfile`, junto a
`libglib2.0-0` y `libgl1` que ya estaban. Ninguna función de GUI de OpenCV se invoca en el
código (no hay `cv2.imshow` ni equivalentes): estas librerías solo son necesarias para que el
`import cv2` no falle, no para mostrar ventanas.

## Alternativas evaluadas

**`pip install paddleocr --no-deps` + declarar manualmente solo las dependencias de runtime
necesarias.** Daría control total y evitaría el peso de `paddlex` (que trae soporte para
docenas de tareas de CV, integración con hubs de modelos como HuggingFace/ModelScope, pandas,
etc., de las que solo se usa una fracción mínima para OCR). Se descartó por ahora: el riesgo de
omitir sin darse cuenta una dependencia que solo falla en tiempo de ejecución (no de
instalación) es alto, y el ahorro de peso no justifica esa fragilidad para un proyecto
académico con foco en el flujo completo. Queda como optimización futura si el tamaño de la
imagen Docker se vuelve un problema medido (no especulado).

**Mantener `opencv-python-headless` y aceptar la duplicación de paquetes.** Se descartó: un
build no determinístico (dos versiones de una misma librería compitiendo por el mismo
namespace, y "ganando" una u otra según el azar del orden de instalación) es un riesgo de
producción inaceptable — el mismo `requirements.txt` podría comportarse distinto entre un build
y otro sin que cambiara ni una línea de código.

## Consecuencias

**A favor**

- Instalación determinística: un solo paquete de opencv, mismo resultado en cada build.
- Sin cambios de código: las subtareas 3 y 4 siguen funcionando tal cual.

**En contra**

- `opencv-contrib-python==4.10.0.84` es una versión más antigua que la `4.14.0.94` fijada
  originalmente (releases más recientes ya corrigen posibles CVEs). Verificar con Trivy tras
  este cambio; si reporta HIGH/CRITICAL, evaluar la alternativa de `--no-deps` descartada
  arriba en vez de forzar una versión más nueva (que volvería a romper la resolución con
  paddlex).
- El Dockerfile crece en tamaño: `opencv-contrib-python` (con GUI) más `paddlepaddle` +
  `paddleocr` (framework de deep learning con modelos) son dependencias pesadas. Es un costo
  aceptado del enfoque elegido en el ADR-0012/0013 (OCR con PaddleOCR, decidido con el usuario
  antes de iniciar la subtarea 3).
- Queda acoplado a la versión exacta de `paddlex`: si se actualiza `paddleocr` en el futuro y
  `paddlex` cambia su versión fijada de `opencv-contrib-python`, hay que volver a alinear
  `requirements.txt` a mano.

## Pendiente

- Confirmar con Trivy (CI) que `opencv-contrib-python==4.10.0.84` no introduce vulnerabilidades
  HIGH/CRITICAL nuevas respecto a la versión anterior.
- Reevaluar `--no-deps` si el tamaño final de la imagen Docker de `ayni-kyc-service` resulta
  problemático para el despliegue en Raspberry Pi 5 (ver ADR-0005).
