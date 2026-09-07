"""Deteccion de que una imagen es un DNI peruano, por geometria (OpenCV).

Etapa 1 del pipeline de verificacion (ver ADR-0012): encuentra el contorno
del documento en la foto, lo endereza con una transformacion de perspectiva
y verifica que su proporcion coincide con el formato ISO/IEC 7810 ID-1 que
usa el DNI peruano. No hace OCR ni valida calidad de imagen: eso es trabajo
de subtareas posteriores (4 y 5), que reutilizaran este mismo pipeline.
"""
import math
from typing import Any

import cv2
import numpy as np
from numpy.typing import NDArray

from src.domain.port.verificador_identidad import AlmacenObjetosPort

# Los stubs de cv2 no son completos (ver override de mypy en pyproject.toml):
# sus funciones no garantizan el dtype exacto de retorno, asi que se tipan
# los arrays intermedios como NDArray[Any] en vez de fingir una precision
# que la propia libreria no ofrece.
Matriz = NDArray[Any]

PROPORCION_DNI_ID1 = 1.586
TOLERANCIA_PROPORCION = 0.15


class DetectorDocumentoOpenCV:
    """Implementa DetectorDocumentoPort mediante deteccion de contornos."""

    def __init__(self, almacen_objetos: AlmacenObjetosPort) -> None:
        self._almacen_objetos = almacen_objetos

    def es_documento_identidad(self, clave_objeto: str) -> bool:
        imagen_bytes = self._almacen_objetos.descargar(clave_objeto)
        imagen = self._decodificar(imagen_bytes)
        if imagen is None:
            return False

        contorno = self._encontrar_contorno_documento(imagen)
        if contorno is None:
            return False

        puntos = self._ordenar_puntos(contorno)
        enderezada = self._enderezar(imagen, puntos)
        return self._proporcion_coincide_con_dni(enderezada)

    def _decodificar(self, imagen_bytes: bytes) -> Matriz | None:
        buffer = np.frombuffer(imagen_bytes, dtype=np.uint8)
        imagen = cv2.imdecode(buffer, cv2.IMREAD_COLOR)
        return imagen if imagen is not None else None

    def _encontrar_contorno_documento(self, imagen: Matriz) -> Matriz | None:
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

    def _ordenar_puntos(self, puntos: Matriz) -> Matriz:
        """Ordena 4 puntos como superior-izq, superior-der, inferior-der, inferior-izq."""
        ordenados = np.zeros((4, 2), dtype=np.float32)
        suma = puntos.sum(axis=1)
        diferencia = np.diff(puntos, axis=1)

        ordenados[0] = puntos[np.argmin(suma)]
        ordenados[2] = puntos[np.argmax(suma)]
        ordenados[1] = puntos[np.argmin(diferencia)]
        ordenados[3] = puntos[np.argmax(diferencia)]
        return ordenados

    def _enderezar(self, imagen: Matriz, puntos: Matriz) -> Matriz:
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

    def _proporcion_coincide_con_dni(self, imagen_enderezada: Matriz) -> bool:
        alto, ancho = imagen_enderezada.shape[:2]
        if alto == 0 or ancho == 0:
            return False

        proporcion = max(ancho, alto) / min(ancho, alto)
        return bool(abs(proporcion - PROPORCION_DNI_ID1) <= TOLERANCIA_PROPORCION)
