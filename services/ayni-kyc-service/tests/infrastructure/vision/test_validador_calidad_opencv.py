"""Pruebas del validador de calidad: sin FastAPI, sin red, sin disco.

Las imagenes sinteticas incluyen textura (lineas/texto), no solo un
rectangulo solido: un relleno de color uniforme tiene varianza de
Laplaciano ~0 y se confundiria con "borrosa" aunque este perfectamente
nitido — el Laplaciano mide bordes/contenido, no nitidez en abstracto.
"""
import cv2
import numpy as np
from numpy.typing import NDArray

from src.infrastructure.vision.validador_calidad_opencv import ValidadorCalidadOpenCV


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


def _lienzo_con_documento(
    ancho_lienzo: int = 600,
    alto_lienzo: int = 600,
    ancho_rect: int = 340,
    alto_rect: int = 214,
) -> tuple[NDArray[np.uint8], int, int]:
    """Lienzo con un rectangulo con textura (simula texto de un DNI)."""
    lienzo = np.full((alto_lienzo, ancho_lienzo, 3), 220, dtype=np.uint8)
    x0 = (ancho_lienzo - ancho_rect) // 2
    y0 = (alto_lienzo - alto_rect) // 2
    cv2.rectangle(lienzo, (x0, y0), (x0 + ancho_rect, y0 + alto_rect), (40, 40, 40), thickness=-1)

    for i in range(5):
        y = y0 + 30 + i * 30
        if y < y0 + alto_rect - 10:
            cv2.line(lienzo, (x0 + 20, y), (x0 + ancho_rect - 20, y), (210, 210, 210), thickness=2)
    cv2.putText(
        lienzo, "DNI 12345678", (x0 + 15, y0 + alto_rect - 15),
        cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 1, cv2.LINE_AA,
    )
    return lienzo, x0, y0


def _imagen_documento_valida() -> bytes:
    lienzo, _, _ = _lienzo_con_documento()
    return _codificar(lienzo)


def _imagen_documento_borrosa() -> bytes:
    lienzo, _, _ = _lienzo_con_documento()
    borrosa = cv2.GaussianBlur(lienzo, (25, 25), 0)
    return _codificar(borrosa)


def _imagen_documento_con_reflejo() -> bytes:
    lienzo, x0, y0 = _lienzo_con_documento()
    # Elipse blanca brillante cubriendo una porcion significativa del documento.
    cv2.ellipse(lienzo, (x0 + 170, y0 + 107), (90, 60), 0, 0, 360, (255, 255, 255), thickness=-1)
    return _codificar(lienzo)


def _imagen_documento_mal_encuadrada() -> bytes:
    """Documento detectable (con margen de fondo en 2 lados) pero que toca
    el borde izquierdo y superior del frame — simula una foto cortada."""
    ancho_lienzo, alto_lienzo = 500, 400
    ancho_rect, alto_rect = 340, 214
    lienzo = np.full((alto_lienzo, ancho_lienzo, 3), 220, dtype=np.uint8)
    x0, y0 = 3, 3
    cv2.rectangle(lienzo, (x0, y0), (x0 + ancho_rect, y0 + alto_rect), (40, 40, 40), thickness=-1)
    for i in range(5):
        y = y0 + 30 + i * 30
        if y < y0 + alto_rect - 10:
            cv2.line(lienzo, (x0 + 20, y), (x0 + ancho_rect - 20, y), (210, 210, 210), thickness=2)
    cv2.putText(
        lienzo, "DNI 12345678", (x0 + 15, y0 + alto_rect - 15),
        cv2.FONT_HERSHEY_SIMPLEX, 0.6, (255, 255, 255), 1, cv2.LINE_AA,
    )
    return _codificar(lienzo)


def test_debe_aprobar_cuando_la_imagen_es_nitida_sin_reflejos_y_bien_encuadrada() -> None:
    # Dado
    almacen = AlmacenObjetosFake({"documento-valido": _imagen_documento_valida()})
    validador = ValidadorCalidadOpenCV(almacen)

    # Cuando
    resultado = validador.validar("documento-valido")

    # Entonces
    assert resultado.es_nitida is True
    assert resultado.sin_reflejos is True
    assert resultado.bien_encuadrada is True
    assert resultado.es_valida is True


def test_debe_rechazar_por_falta_de_nitidez_cuando_la_imagen_esta_borrosa() -> None:
    # Dado
    almacen = AlmacenObjetosFake({"documento-borroso": _imagen_documento_borrosa()})
    validador = ValidadorCalidadOpenCV(almacen)

    # Cuando
    resultado = validador.validar("documento-borroso")

    # Entonces
    assert resultado.es_nitida is False
    assert resultado.es_valida is False


def test_debe_rechazar_por_reflejo_cuando_hay_una_zona_muy_brillante() -> None:
    # Dado
    almacen = AlmacenObjetosFake({"documento-con-reflejo": _imagen_documento_con_reflejo()})
    validador = ValidadorCalidadOpenCV(almacen)

    # Cuando
    resultado = validador.validar("documento-con-reflejo")

    # Entonces
    assert resultado.sin_reflejos is False
    assert resultado.es_valida is False


def test_debe_rechazar_por_encuadre_cuando_el_documento_toca_el_borde_de_la_foto() -> None:
    # Dado
    almacen = AlmacenObjetosFake({"documento-mal-encuadrado": _imagen_documento_mal_encuadrada()})
    validador = ValidadorCalidadOpenCV(almacen)

    # Cuando
    resultado = validador.validar("documento-mal-encuadrado")

    # Entonces
    assert resultado.bien_encuadrada is False
    assert resultado.es_valida is False


def test_debe_rechazar_los_tres_controles_cuando_los_bytes_no_son_una_imagen_valida() -> None:
    # Dado
    almacen = AlmacenObjetosFake({"no-es-imagen": b"esto no es una imagen"})
    validador = ValidadorCalidadOpenCV(almacen)

    # Cuando
    resultado = validador.validar("no-es-imagen")

    # Entonces
    assert resultado.es_nitida is False
    assert resultado.sin_reflejos is False
    assert resultado.bien_encuadrada is False
    assert resultado.es_valida is False
