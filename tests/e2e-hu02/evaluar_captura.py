"""Evalua fotos de un lado del DNI con el mismo codigo que kyc-service, sin gastar intentos.

Sirve en la prueba E2E con camara: en la pagina de captura, «Tomar foto» no gasta intento y
solo «Usar esta foto» envia (y descuenta si se rechaza). Se exporta la foto del navegador (el
`blob:` de la imagen de revision, en base64), se pasa por aqui y solo se envia si el veredicto
es ACEPTADA.

Reutiliza `_evaluar` del router de evaluacion (el camino real de una foto) y las constantes de
los validadores, de modo que el veredicto y los umbrales no se desincronizan del servicio.

Uso, dentro del contenedor (el script y las fotos se copian con `docker cp`):

    docker cp tests/e2e-hu02/evaluar_captura.py ayni-kyc-service:/tmp/
    docker cp foto.jpg ayni-kyc-service:/tmp/foto.jpg
    docker exec -w /app -e PYTHONPATH=/app ayni-kyc-service python /tmp/evaluar_captura.py /tmp/foto.jpg

Cada argumento es una imagen (.jpg, .png, .webp) o un .json con una lista de imagenes en
base64 (lo que devuelve `evaluate_script` con `filePath`). Termina con codigo 0 solo si todas
las fotos se aceptan. No imprime ni conserva datos personales: solo medidas.

Las fotos de un DNI real son datos personales: borrarlas del contenedor y del disco al terminar.
"""
import base64
import json
import sys
from pathlib import Path

import cv2
import numpy as np

from src.infrastructure.vision.detector_documento_opencv import (
    PROPORCION_DNI_ID1,
    TOLERANCIA_PROPORCION,
)
from src.infrastructure.vision.procesamiento_documento import (
    decodificar_imagen,
    procesar_documento,
)
from src.infrastructure.vision.validador_calidad_opencv import (
    BRILLO_MEDIO_MAXIMO,
    BRILLO_MEDIO_MINIMO,
    FRACCION_MAXIMA_REFLEJO,
    UMBRAL_BRILLO_REFLEJO,
    UMBRAL_NITIDEZ,
)
from src.infrastructure.web.router_documentos import _evaluar


def _cargar(ruta: str) -> list[tuple[str, bytes]]:
    contenido = Path(ruta).read_bytes()
    if ruta.lower().endswith(".json"):
        return [(f"{ruta}[{i}]", base64.b64decode(b64)) for i, b64 in enumerate(json.loads(contenido))]
    return [(ruta, contenido)]


def _contorno_mayor(imagen: np.ndarray) -> str:
    """Por que no se cierra un contorno: el mayor que ve el detector (Canny 75-200)."""
    gris = cv2.cvtColor(imagen, cv2.COLOR_BGR2GRAY)
    bordes = cv2.Canny(cv2.GaussianBlur(gris, (5, 5), 0), 75, 200)
    contornos, _ = cv2.findContours(bordes, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    if not contornos:
        return "ningun contorno"
    mayor = max(contornos, key=cv2.contourArea)
    vertices = len(cv2.approxPolyDP(mayor, 0.02 * cv2.arcLength(mayor, True), True))
    fraccion = cv2.contourArea(mayor) / (imagen.shape[0] * imagen.shape[1])
    return f"el mayor tiene {vertices} vertices y el {100 * fraccion:.1f} % del frame (se necesitan 4)"


def _contraste(imagen: np.ndarray) -> str:
    """Salto de gris entre la zona clara (tarjeta) y la oscura (fondo), por Otsu."""
    gris = cv2.cvtColor(imagen, cv2.COLOR_BGR2GRAY)
    umbral, _ = cv2.threshold(cv2.GaussianBlur(gris, (5, 5), 0), 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    clara = gris > umbral
    if not clara.any() or clara.all():
        return "sin salto claro/oscuro"
    return (f"{gris[clara].mean() - gris[~clara].mean():.0f} "
            f"(tarjeta {gris[clara].mean():.0f}, fondo {gris[~clara].mean():.0f}; "
            "con menos de ~80 el borde de la tarjeta suele no cerrarse)")


def _evaluar_foto(nombre: str, datos: bytes) -> bool:
    imagen = decodificar_imagen(datos)
    if imagen is None:
        print(f"{nombre}\n  veredicto: RECHAZADA (NO_ES_DNI): los bytes no son una imagen\n")
        return False

    resultado = _evaluar(datos, None)  # el detector y el validador no usan el almacen
    alto, ancho = imagen.shape[:2]
    veredicto = "ACEPTADA" if resultado.aceptada else f"RECHAZADA ({resultado.motivo_rechazo.value})"
    print(f"{nombre}  ({ancho}x{alto})")
    print(f"  veredicto: {veredicto}")

    procesado = procesar_documento(datos)
    if procesado is None:
        print(f"  contorno: no se cerro uno de 4 vertices; {_contorno_mayor(imagen)}")
        medida = imagen
        origen = "foto entera"
    else:
        medida = procesado.imagen_enderezada
        alto_doc, ancho_doc = medida.shape[:2]
        proporcion = max(alto_doc, ancho_doc) / min(alto_doc, ancho_doc)
        fraccion = cv2.contourArea(procesado.contorno) / (alto * ancho)
        print(f"  contorno: 4 vertices, {100 * fraccion:.1f} % del frame, documento {ancho_doc}x{alto_doc}")
        print(f"  proporcion: {proporcion:.2f} (ID-1 {PROPORCION_DNI_ID1} +- {TOLERANCIA_PROPORCION})")
        origen = "documento enderezado"

    gris = cv2.cvtColor(medida, cv2.COLOR_BGR2GRAY)
    nitidez = cv2.Laplacian(gris, cv2.CV_64F).var()
    saturados = 100 * float((gris > UMBRAL_BRILLO_REFLEJO).mean())
    print(f"  nitidez ({origen}): {nitidez:.1f} (minimo {UMBRAL_NITIDEZ:g})")
    print(f"  reflejo ({origen}): {saturados:.1f} % de pixeles > {UMBRAL_BRILLO_REFLEJO} "
          f"(maximo {100 * FRACCION_MAXIMA_REFLEJO:g} %)")
    brillo = float(cv2.cvtColor(imagen, cv2.COLOR_BGR2GRAY).mean())
    print(f"  iluminacion: brillo medio del frame {brillo:.0f} "
          f"(entre {BRILLO_MEDIO_MINIMO:g} y {BRILLO_MEDIO_MAXIMO:g})")
    print(f"  contraste tarjeta/fondo: {_contraste(imagen)}\n")
    return resultado.aceptada


def main(rutas: list[str]) -> int:
    if not rutas:
        print(__doc__)
        return 2
    aceptadas = [_evaluar_foto(nombre, datos) for ruta in rutas for nombre, datos in _cargar(ruta)]
    return 0 if all(aceptadas) else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
