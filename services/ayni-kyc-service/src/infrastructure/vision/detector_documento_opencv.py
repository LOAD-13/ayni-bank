"""Deteccion de que una imagen es un DNI peruano, por geometria (OpenCV).

Etapa 1 del pipeline de verificacion (ver ADR-0012): usa procesamiento_documento
para encontrar y enderezar el documento, y verifica que su proporcion coincide
con el formato ISO/IEC 7810 ID-1 que usa el DNI peruano. No hace OCR ni valida
calidad de imagen: eso es trabajo de subtareas posteriores (4 y 5).
"""
from typing import Any

import cv2
from numpy.typing import NDArray

from src.domain.port.verificador_identidad import AlmacenObjetosPort
from src.infrastructure.vision.procesamiento_documento import DocumentoProcesado, procesar_documento

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

        return self.coincide_con_dni(procesado)

    def coincide_con_dni(self, procesado: DocumentoProcesado) -> bool:
        """Evalua un documento ya descargado y enderezado, sin volver a leer MinIO."""
        return self._proporcion_coincide_con_dni(procesado.imagen_enderezada)

    def _proporcion_coincide_con_dni(self, imagen_enderezada: Matriz) -> bool:
        alto, ancho = imagen_enderezada.shape[:2]
        if alto == 0 or ancho == 0:
            return False

        proporcion = max(ancho, alto) / min(ancho, alto)
        return bool(abs(proporcion - PROPORCION_DNI_ID1) <= TOLERANCIA_PROPORCION)


# Ancho al que se normaliza la tarjeta antes de buscar el rostro: a esa escala la foto
# del titular mide unos 50 px y la huella o el codigo de barras del reverso no se
# confunden con una cara (a doble escala si lo hacen).
ANCHO_NORMALIZADO = 400
# En el anverso la foto del titular esta en el tercio izquierdo de la tarjeta.
FRACCION_ZONA_DE_LA_FOTO = 0.40
ALTO_MINIMO_DEL_ROSTRO = 0.15


def tiene_rostro_del_titular(imagen_enderezada: Matriz) -> bool:
    """Si la tarjeta muestra la foto del titular, es decir, si es el anverso.

    Solo sirve para rechazar un anverso enviado como reverso: si no se encuentra un rostro
    no se concluye nada, porque una foto girada o con poca luz tambien lo esconde.
    """
    alto, ancho = imagen_enderezada.shape[:2]
    if alto == 0 or ancho == 0:
        return False
    escala = ANCHO_NORMALIZADO / ancho
    tarjeta = cv2.resize(imagen_enderezada, (ANCHO_NORMALIZADO, max(1, int(alto * escala))))
    gris = cv2.equalizeHist(cv2.cvtColor(tarjeta, cv2.COLOR_BGR2GRAY))
    clasificador = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")
    alto_minimo = int(gris.shape[0] * ALTO_MINIMO_DEL_ROSTRO)
    rostros = clasificador.detectMultiScale(gris, 1.1, 5, minSize=(alto_minimo, alto_minimo))
    limite = ANCHO_NORMALIZADO * FRACCION_ZONA_DE_LA_FOTO
    return any(x + w / 2 < limite for (x, _y, w, _h) in rostros)
