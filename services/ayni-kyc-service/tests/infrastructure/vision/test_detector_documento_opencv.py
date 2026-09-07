"""Pruebas del detector de documentos: sin FastAPI, sin red, sin disco.

Las imagenes se generan sinteticamente con OpenCV (no hay dataset de DNI
reales en el repo): un rectangulo con la proporcion ISO/IEC 7810 ID-1 sobre
un fondo, para simular una foto de documento sobre una mesa.
"""
import cv2
import numpy as np
from numpy.typing import NDArray

from src.infrastructure.vision.detector_documento_opencv import DetectorDocumentoOpenCV


class AlmacenObjetosFake:
    """Implementacion en memoria de AlmacenObjetosPort, solo para pruebas."""

    def __init__(self, objetos: dict[str, bytes]) -> None:
        self._objetos = objetos

    def descargar(self, clave_objeto: str) -> bytes:
        return self._objetos[clave_objeto]

    def calcular_hash(self, clave_objeto: str) -> str:
        raise NotImplementedError("No usado en estas pruebas")


def _codificar(imagen: NDArray[np.uint8]) -> bytes:
    ok, buffer = cv2.imencode(".png", imagen)
    assert ok
    return bytes(buffer)


def _imagen_con_rectangulo(ancho_rect: int, alto_rect: int) -> bytes:
    """Lienzo con un rectangulo relleno centrado, simulando una foto de documento."""
    lienzo = np.full((500, 500, 3), 220, dtype=np.uint8)
    x0 = (500 - ancho_rect) // 2
    y0 = (500 - alto_rect) // 2
    cv2.rectangle(lienzo, (x0, y0), (x0 + ancho_rect, y0 + alto_rect), (40, 40, 40), thickness=-1)
    return _codificar(lienzo)


def test_debe_detectar_documento_cuando_la_imagen_tiene_proporcion_de_dni() -> None:
    # Dado: un rectangulo 340x214 (~1.588, dentro de tolerancia de 1.586)
    imagen_bytes = _imagen_con_rectangulo(ancho_rect=340, alto_rect=214)
    almacen = AlmacenObjetosFake({"documento-valido": imagen_bytes})
    detector = DetectorDocumentoOpenCV(almacen)

    # Cuando / Entonces
    assert detector.es_documento_identidad("documento-valido") is True


def test_debe_rechazar_cuando_la_imagen_no_tiene_proporcion_de_dni() -> None:
    # Dado: un cuadrado (proporcion 1.0, muy lejos de 1.586)
    imagen_bytes = _imagen_con_rectangulo(ancho_rect=250, alto_rect=250)
    almacen = AlmacenObjetosFake({"documento-invalido": imagen_bytes})
    detector = DetectorDocumentoOpenCV(almacen)

    # Cuando / Entonces
    assert detector.es_documento_identidad("documento-invalido") is False


def test_debe_rechazar_cuando_los_bytes_no_son_una_imagen_valida() -> None:
    # Dado
    almacen = AlmacenObjetosFake({"no-es-imagen": b"esto no es una imagen"})
    detector = DetectorDocumentoOpenCV(almacen)

    # Cuando / Entonces
    assert detector.es_documento_identidad("no-es-imagen") is False
