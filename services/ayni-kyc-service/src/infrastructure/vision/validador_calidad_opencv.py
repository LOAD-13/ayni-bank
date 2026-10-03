"""Validacion de nitidez, reflejos, encuadre e iluminacion de la foto de un documento (OpenCV).

Etapa posterior a la deteccion (subtarea 3): reutiliza procesamiento_documento
para no repetir la busqueda de contorno, y evalua cuatro controles de calidad
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
MARGEN_REFLEJO = 0.06
MARGEN_BORDE_MINIMO = 0.02
FRACCION_AREA_MINIMA = 0.15
FRACCION_AREA_MAXIMA = 0.95
# Brillo medio (0-255) de la foto completa. Se mide sobre el encuadre entero y no
# sobre el documento: la falta o el exceso de luz afecta a toda la toma, mientras
# que un reflejo es local y ya lo cubre _sin_reflejos.
BRILLO_MEDIO_MINIMO = 40.0
BRILLO_MEDIO_MAXIMO = 235.0


class ValidadorCalidadOpenCV:
    """Implementa ValidadorCalidadPort mediante metricas clasicas de OpenCV."""

    def __init__(self, almacen_objetos: AlmacenObjetosPort) -> None:
        self._almacen_objetos = almacen_objetos

    def validar(self, clave_objeto: str) -> ResultadoValidacionCalidad:
        imagen_bytes = self._almacen_objetos.descargar(clave_objeto)
        procesado = procesar_documento(imagen_bytes)
        if procesado is None:
            return ResultadoValidacionCalidad(
                es_nitida=False, sin_reflejos=False, bien_encuadrada=False, bien_iluminada=False
            )

        return self.evaluar(procesado)

    def evaluar(self, procesado: DocumentoProcesado) -> ResultadoValidacionCalidad:
        """Evalua un documento ya descargado y enderezado, sin volver a leer MinIO."""
        return ResultadoValidacionCalidad(
            es_nitida=self._es_nitida(procesado.imagen_enderezada),
            sin_reflejos=self._sin_reflejos(procesado.imagen_enderezada),
            bien_encuadrada=self._bien_encuadrada(procesado),
            bien_iluminada=self._bien_iluminada(procesado.imagen_original),
        )

    def evaluar_sin_documento(self, imagen: Matriz) -> ResultadoValidacionCalidad:
        """Mide la foto entera cuando no se encontro el contorno de un documento.

        Sirve para distinguir "no hay un DNI" de "hay un DNI pero la foto esta
        borrosa u oscura", que es lo que impide encontrar el contorno. El
        encuadre no se puede medir sin documento y se da por no valido.
        """
        return ResultadoValidacionCalidad(
            es_nitida=self._es_nitida(imagen),
            sin_reflejos=self._sin_reflejos(imagen),
            bien_encuadrada=False,
            bien_iluminada=self._bien_iluminada(imagen),
        )

    def _es_nitida(self, imagen_enderezada: Matriz) -> bool:
        gris = cv2.cvtColor(imagen_enderezada, cv2.COLOR_BGR2GRAY)
        varianza_laplaciano = cv2.Laplacian(gris, cv2.CV_64F).var()
        return bool(varianza_laplaciano >= UMBRAL_NITIDEZ)

    def _sin_reflejos(self, imagen_enderezada: Matriz) -> bool:
        """Un reflejo es una mancha brillante continua, no pixeles blancos sueltos.

        Se ignora una franja junto al borde (las esquinas redondeadas de un DNI
        escaneado dejan fondo blanco) y se mide la mayor zona saturada: el texto
        claro, los hologramas y el fondo del DNI no forman una mancha grande.
        """
        gris = cv2.cvtColor(imagen_enderezada, cv2.COLOR_BGR2GRAY)
        alto, ancho = gris.shape[:2]
        margen_y, margen_x = int(alto * MARGEN_REFLEJO), int(ancho * MARGEN_REFLEJO)
        interior = gris[margen_y : alto - margen_y, margen_x : ancho - margen_x]
        if interior.size == 0:
            return False

        _, saturados = cv2.threshold(interior, UMBRAL_BRILLO_REFLEJO, 255, cv2.THRESH_BINARY)
        cantidad, _, estadisticas, _ = cv2.connectedComponentsWithStats(saturados)
        if cantidad <= 1:
            return True
        mancha_mayor = int(estadisticas[1:, cv2.CC_STAT_AREA].max())
        return bool(mancha_mayor / interior.size <= FRACCION_MAXIMA_REFLEJO)

    def _bien_encuadrada(self, procesado: DocumentoProcesado) -> bool:
        if procesado.recortada_al_borde:
            return True
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

    def _bien_iluminada(self, imagen_original: Matriz) -> bool:
        gris = cv2.cvtColor(imagen_original, cv2.COLOR_BGR2GRAY)
        brillo_medio = float(gris.mean())
        return BRILLO_MEDIO_MINIMO <= brillo_medio <= BRILLO_MEDIO_MAXIMO
