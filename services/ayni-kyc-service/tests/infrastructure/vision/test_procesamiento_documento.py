"""Casos reales que el detector de contornos no reconocia y mandaban al cliente a revision manual."""
import cv2
import numpy as np
from numpy.typing import NDArray

from src.infrastructure.vision.detector_documento_opencv import DetectorDocumentoOpenCV
from src.infrastructure.vision.procesamiento_documento import procesar_documento
from src.infrastructure.vision.validador_calidad_opencv import ValidadorCalidadOpenCV


def _codificar(imagen: NDArray[np.uint8]) -> bytes:
    exito, buffer = cv2.imencode(".png", imagen)
    assert exito
    return buffer.tobytes()


def _tarjeta(ancho: int = 400, alto: int = 252) -> NDArray[np.uint8]:
    """Una tarjeta con la proporcion del DNI, fondo celeste y lineas de texto oscuras."""
    tarjeta = np.full((alto, ancho, 3), (215, 200, 150), dtype=np.uint8)
    for fila in range(30, alto - 20, 22):
        cv2.putText(tarjeta, "LOA DENEGRI 76362635", (20, fila), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (40, 40, 40), 1)
    cv2.rectangle(tarjeta, (20, 40), (110, 160), (60, 60, 60), thickness=-1)
    return tarjeta


def _evaluar(imagen: NDArray[np.uint8]) -> tuple[bool, bool]:
    procesado = procesar_documento(_codificar(imagen))
    assert procesado is not None
    calidad = ValidadorCalidadOpenCV(None).evaluar(procesado)  # type: ignore[arg-type]
    return DetectorDocumentoOpenCV(None).coincide_con_dni(procesado), calidad.bien_encuadrada  # type: ignore[arg-type]


def test_debe_aceptar_un_dni_escaneado_y_recortado_al_borde() -> None:
    es_dni, encuadre = _evaluar(_tarjeta())

    assert es_dni is True
    assert encuadre is True


def test_debe_encontrar_una_tarjeta_de_esquinas_redondeadas_sobre_una_mesa() -> None:
    mesa = np.full((700, 900, 3), (70, 90, 110), dtype=np.uint8)
    mascara = np.zeros(mesa.shape[:2], dtype=np.uint8)
    cv2.rectangle(mascara, (220, 210), (680, 500), 255, thickness=-1)
    for x, y in ((220, 210), (680, 210), (220, 500), (680, 500)):
        cv2.circle(mascara, (x, y), 22, 0, thickness=-1)
    for x, y in ((242, 232), (658, 232), (242, 478), (658, 478)):
        cv2.circle(mascara, (x, y), 22, 255, thickness=-1)
    tarjeta = cv2.resize(_tarjeta(), (461, 291))
    lienzo = mesa.copy()
    lienzo[210:501, 220:681] = tarjeta
    foto = np.where(mascara[..., None] == 255, lienzo, mesa).astype(np.uint8)

    es_dni, encuadre = _evaluar(foto)

    assert es_dni is True
    assert encuadre is True


def test_no_debe_tratar_como_documento_una_imagen_sin_contorno_ni_proporcion_de_dni() -> None:
    franja = np.full((200, 800, 3), 180, dtype=np.uint8)

    assert procesar_documento(_codificar(franja)) is None


def test_debe_tratar_como_recortado_un_dni_cuyo_borde_queda_pegado_al_marco() -> None:
    # Un escaneo con un filo de fondo: el contorno de la tarjeta toca el marco y antes
    # se rechazaba por encuadre aunque el DNI estaba entero.
    lienzo = np.full((262, 410, 3), 245, dtype=np.uint8)
    lienzo[3:258, 4:406] = cv2.resize(_tarjeta(), (402, 255))

    es_dni, encuadre = _evaluar(lienzo)

    assert es_dni is True
    assert encuadre is True
