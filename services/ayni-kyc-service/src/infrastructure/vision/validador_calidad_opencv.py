"""Validacion de nitidez, reflejos y encuadre de la foto de un documento (OpenCV).

Etapa posterior a la deteccion (subtarea 3): reutiliza procesamiento_documento
para no repetir la busqueda de contorno, y evalua tres controles de calidad
antes de pasar la imagen al OCR (subtarea 5). Los umbrales son heuristicas
iniciales, sin conjunto de control para HU-02 (ver Pendiente en ADR-0012);
requieren calibracion cuando exista evidencia empirica.
"""
from typing import Any

import cv2
from numpy.typing import NDArray

from src.domain.model.resultado_verificacion import ResultadoValidacionCalidad
from src.domain.port.verificador_identidad import AlmacenObjetosPort
from src.infrastructure.vision.procesamiento_documento import DocumentoProcesado, procesar_documento

Matriz = NDArray[Any]

UMBRAL_NITIDEZ = 100.0
UMBRAL_BRILLO_REFLEJO = 240
FRACCION_MAXIMA_REFLEJO = 0.05
MARGEN_BORDE_MINIMO = 0.02
FRACCION_AREA_MINIMA = 0.15
FRACCION_AREA_MAXIMA = 0.95


class ValidadorCalidadOpenCV:
    """Implementa ValidadorCalidadPort mediante metricas clasicas de OpenCV."""

    def __init__(self, almacen_objetos: AlmacenObjetosPort) -> None:
        self._almacen_objetos = almacen_objetos

    def validar(self, clave_objeto: str) -> ResultadoValidacionCalidad:
        imagen_bytes = self._almacen_objetos.descargar(clave_objeto)
        procesado = procesar_documento(imagen_bytes)
        if procesado is None:
            return ResultadoValidacionCalidad(
                es_nitida=False, sin_reflejos=False, bien_encuadrada=False
            )

        return ResultadoValidacionCalidad(
            es_nitida=self._es_nitida(procesado.imagen_enderezada),
            sin_reflejos=self._sin_reflejos(procesado.imagen_enderezada),
            bien_encuadrada=self._bien_encuadrada(procesado),
        )

    def _es_nitida(self, imagen_enderezada: Matriz) -> bool:
        gris = cv2.cvtColor(imagen_enderezada, cv2.COLOR_BGR2GRAY)
        varianza_laplaciano = cv2.Laplacian(gris, cv2.CV_64F).var()
        return bool(varianza_laplaciano >= UMBRAL_NITIDEZ)

    def _sin_reflejos(self, imagen_enderezada: Matriz) -> bool:
        gris = cv2.cvtColor(imagen_enderezada, cv2.COLOR_BGR2GRAY)
        _, saturados = cv2.threshold(gris, UMBRAL_BRILLO_REFLEJO, 255, cv2.THRESH_BINARY)

        total_pixeles = saturados.size
        if total_pixeles == 0:
            return False

        pixeles_saturados = cv2.countNonZero(saturados)
        fraccion_saturada = pixeles_saturados / total_pixeles
        return bool(fraccion_saturada <= FRACCION_MAXIMA_REFLEJO)

    def _bien_encuadrada(self, procesado: DocumentoProcesado) -> bool:
        alto_frame, ancho_frame = procesado.imagen_original.shape[:2]
        if alto_frame == 0 or ancho_frame == 0:
            return False

        margen_x = ancho_frame * MARGEN_BORDE_MINIMO
        margen_y = alto_frame * MARGEN_BORDE_MINIMO
        for x, y in procesado.contorno:
            if x <= margen_x or x >= ancho_frame - margen_x:
                return False
            if y <= margen_y or y >= alto_frame - margen_y:
                return False

        area_contorno = cv2.contourArea(procesado.contorno)
        area_frame = ancho_frame * alto_frame
        fraccion_area = area_contorno / area_frame
        return FRACCION_AREA_MINIMA <= fraccion_area <= FRACCION_AREA_MAXIMA
