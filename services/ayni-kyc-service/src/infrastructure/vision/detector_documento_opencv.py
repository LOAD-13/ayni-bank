"""Deteccion de que una imagen es un DNI peruano, por geometria (OpenCV).

Etapa 1 del pipeline de verificacion (ver ADR-0012): usa procesamiento_documento
para encontrar y enderezar el documento, y verifica que su proporcion coincide
con el formato ISO/IEC 7810 ID-1 que usa el DNI peruano. No hace OCR ni valida
calidad de imagen: eso es trabajo de subtareas posteriores (4 y 5).
"""
from typing import Any

from numpy.typing import NDArray

from src.domain.port.verificador_identidad import AlmacenObjetosPort
from src.infrastructure.vision.procesamiento_documento import procesar_documento

Matriz = NDArray[Any]

PROPORCION_DNI_ID1 = 1.586
TOLERANCIA_PROPORCION = 0.15


class DetectorDocumentoOpenCV:
    """Implementa DetectorDocumentoPort mediante deteccion de contornos."""

    def __init__(self, almacen_objetos: AlmacenObjetosPort) -> None:
        self._almacen_objetos = almacen_objetos

    def es_documento_identidad(self, clave_objeto: str) -> bool:
        imagen_bytes = self._almacen_objetos.descargar(clave_objeto)
        procesado = procesar_documento(imagen_bytes)
        if procesado is None:
            return False

        return self._proporcion_coincide_con_dni(procesado.imagen_enderezada)

    def _proporcion_coincide_con_dni(self, imagen_enderezada: Matriz) -> bool:
        alto, ancho = imagen_enderezada.shape[:2]
        if alto == 0 or ancho == 0:
            return False

        proporcion = max(ancho, alto) / min(ancho, alto)
        return bool(abs(proporcion - PROPORCION_DNI_ID1) <= TOLERANCIA_PROPORCION)
