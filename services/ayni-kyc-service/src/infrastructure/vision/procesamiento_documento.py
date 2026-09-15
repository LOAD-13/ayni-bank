"""Pipeline compartido: encontrar el contorno de un documento en una foto y enderezarlo.

Etapa comun reutilizada por DetectorDocumentoOpenCV (subtarea 3) y
ValidadorCalidadOpenCV (subtarea 4), para no repetir la deteccion de contorno
en cada etapa (ver ADR-0012).
"""
import math
from dataclasses import dataclass
from typing import Any

import cv2
import numpy as np
from numpy.typing import NDArray

# Los stubs de cv2 no son completos (ver override de mypy en pyproject.toml):
# sus funciones no garantizan el dtype exacto de retorno, asi que se tipan
# los arrays intermedios como NDArray[Any] en vez de fingir una precision
# que la propia libreria no ofrece.
Matriz = NDArray[Any]


@dataclass(frozen=True)
class DocumentoProcesado:
    """Resultado de encontrar y enderezar un documento en una foto.

    `contorno` esta en las coordenadas de `imagen_original` (util para
    validar encuadre); `imagen_enderezada` es el documento recortado y
    puesto de frente (util para proporcion, nitidez, reflejos y OCR).
    """

    imagen_original: Matriz
    contorno: Matriz
    imagen_enderezada: Matriz


def procesar_documento(imagen_bytes: bytes) -> DocumentoProcesado | None:
    """Decodifica la imagen, encuentra el contorno del documento y lo endereza.

    Retorna None si los bytes no son una imagen valida o no se encontro un
    contorno cuadrilatero reconocible como documento.
    """
    imagen = _decodificar(imagen_bytes)
    if imagen is None:
        return None

    contorno = _encontrar_contorno_documento(imagen)
    if contorno is None:
        return None

    puntos = _ordenar_puntos(contorno)
    enderezada = _enderezar(imagen, puntos)
    return DocumentoProcesado(imagen_original=imagen, contorno=puntos, imagen_enderezada=enderezada)


def _decodificar(imagen_bytes: bytes) -> Matriz | None:
    buffer = np.frombuffer(imagen_bytes, dtype=np.uint8)
    imagen = cv2.imdecode(buffer, cv2.IMREAD_COLOR)
    return imagen if imagen is not None else None


def _encontrar_contorno_documento(imagen: Matriz) -> Matriz | None:
    gris = cv2.cvtColor(imagen, cv2.COLOR_BGR2GRAY)
    desenfocada = cv2.GaussianBlur(gris, (5, 5), 0)
    bordes = cv2.Canny(desenfocada, 75, 200)

    contornos, _ = cv2.findContours(bordes, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    if not contornos:
        return None

    contorno_mayor = max(contornos, key=cv2.contourArea)
    perimetro = cv2.arcLength(contorno_mayor, True)
    aproximado = cv2.approxPolyDP(contorno_mayor, 0.02 * perimetro, True)

    if len(aproximado) != 4:
        return None
    return aproximado.reshape(4, 2)


def _ordenar_puntos(puntos: Matriz) -> Matriz:
    """Ordena 4 puntos como superior-izq, superior-der, inferior-der, inferior-izq."""
    ordenados = np.zeros((4, 2), dtype=np.float32)
    suma = puntos.sum(axis=1)
    diferencia = np.diff(puntos, axis=1)

    ordenados[0] = puntos[np.argmin(suma)]
    ordenados[2] = puntos[np.argmax(suma)]
    ordenados[1] = puntos[np.argmin(diferencia)]
    ordenados[3] = puntos[np.argmax(diferencia)]
    return ordenados


def _enderezar(imagen: Matriz, puntos: Matriz) -> Matriz:
    (sup_izq, sup_der, inf_der, inf_izq) = puntos

    ancho_superior = math.dist(sup_izq, sup_der)
    ancho_inferior = math.dist(inf_izq, inf_der)
    ancho_max = max(int(ancho_superior), int(ancho_inferior))

    alto_izquierdo = math.dist(sup_izq, inf_izq)
    alto_derecho = math.dist(sup_der, inf_der)
    alto_max = max(int(alto_izquierdo), int(alto_derecho))

    if ancho_max == 0 or alto_max == 0:
        return imagen

    destino = np.array(
        [[0, 0], [ancho_max - 1, 0], [ancho_max - 1, alto_max - 1], [0, alto_max - 1]],
        dtype=np.float32,
    )
    matriz = cv2.getPerspectiveTransform(puntos, destino)
    return cv2.warpPerspective(imagen, matriz, (ancho_max, alto_max))
