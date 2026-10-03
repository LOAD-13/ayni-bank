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
    # True cuando la imagen ya es el documento recortado (un escaneo o una foto
    # recortada al borde de la tarjeta): no hay fondo alrededor y el encuadre
    # no se puede ni se necesita medir.
    recortada_al_borde: bool = False


def procesar_documento(imagen_bytes: bytes) -> DocumentoProcesado | None:
    """Decodifica la imagen, encuentra el contorno del documento y lo endereza.

    Retorna None si los bytes no son una imagen valida o no se encontro un
    contorno cuadrilatero reconocible como documento.
    """
    imagen = decodificar_imagen(imagen_bytes)
    if imagen is None:
        return None

    candidatos = [_enderezado(imagen, contorno) for contorno in _contornos_candidatos(imagen)]
    con_forma_de_dni = [
        candidato
        for candidato in candidatos
        if _tiene_proporcion_id1(candidato.imagen_enderezada)
        and not _ocupa_toda_la_imagen(candidato.contorno, imagen)
    ]
    if con_forma_de_dni:
        return con_forma_de_dni[0]

    recortada = _como_documento_recortado(imagen)
    if recortada is not None:
        return recortada
    # Ninguno tiene la forma del DNI: se devuelve el mayor para que el detector
    # informe "no es un DNI" con un objeto nitido delante.
    return candidatos[0] if candidatos else None


def _enderezado(imagen: Matriz, contorno: Matriz) -> DocumentoProcesado:
    puntos = _ordenar_puntos(contorno)
    return DocumentoProcesado(imagen_original=imagen, contorno=puntos, imagen_enderezada=_enderezar(imagen, puntos))


def _tiene_proporcion_id1(imagen: Matriz) -> bool:
    alto, ancho = imagen.shape[:2]
    if alto == 0 or ancho == 0:
        return False
    return abs(max(ancho, alto) / min(ancho, alto) - _PROPORCION_ID1) <= _TOLERANCIA_ID1


def decodificar_imagen(imagen_bytes: bytes) -> Matriz | None:
    """Bytes de JPEG/PNG/WEBP a matriz BGR; None si no son una imagen que OpenCV lea."""
    buffer = np.frombuffer(imagen_bytes, dtype=np.uint8)
    imagen = cv2.imdecode(buffer, cv2.IMREAD_COLOR)
    return imagen if imagen is not None else None


# Proporcion ISO/IEC 7810 ID-1 del DNI y su tolerancia. Se repiten aqui (y no se
# importan del detector) para no crear una dependencia circular entre etapas.
_PROPORCION_ID1 = 1.586
_TOLERANCIA_ID1 = 0.15
# Un contorno menor a esta fraccion de la foto es un detalle de la tarjeta (la
# foto, un recuadro), no la tarjeta.
_FRACCION_AREA_MINIMA_CONTORNO = 0.10
# Cuanto del rectangulo minimo tiene que llenar un contorno para tratarlo como
# una tarjeta de esquinas redondeadas.
_RELLENO_MINIMO_RECTANGULO = 0.85
_CANDIDATOS = 8
# Un contorno que cubre casi toda la imagen es el borde de un documento ya
# recortado: se usa la imagen entera en vez de un contorno pegado a los bordes.
_FRACCION_AREA_RECORTADA = 0.85
# Misma franja que usa el validador para el encuadre: un contorno dentro de ella esta
# pegado al marco, que es lo que pasa con un DNI recortado al borde.
_MARGEN_DEL_MARCO = 0.02


def _contornos_candidatos(imagen: Matriz) -> list[Matriz]:
    """Cuadrilateros que podrian ser la tarjeta, del mas grande al mas pequeno.

    Se devuelven varios porque el mayor no siempre es la tarjeta: en un DNI
    escaneado el mayor contorno interior es la foto del titular.

    Los bordes de Canny llegan cortados en las esquinas redondeadas del DNI y en
    los tramos donde la tarjeta se parece al fondo; se cierran con una dilatacion
    antes de buscar contornos. Si el contorno no se reduce a 4 vertices con
    ninguna tolerancia, pero llena casi todo su rectangulo minimo, se usa ese
    rectangulo: es una tarjeta con las esquinas redondeadas.
    """
    gris = cv2.cvtColor(imagen, cv2.COLOR_BGR2GRAY)
    desenfocada = cv2.GaussianBlur(gris, (5, 5), 0)
    bordes = cv2.Canny(desenfocada, 50, 150)
    bordes = cv2.dilate(bordes, np.ones((3, 3), np.uint8), iterations=2)

    contornos, _ = cv2.findContours(bordes, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    candidatos: list[Matriz] = []
    area_imagen = float(imagen.shape[0] * imagen.shape[1])
    for contorno in sorted(contornos, key=cv2.contourArea, reverse=True)[:_CANDIDATOS]:
        area = cv2.contourArea(contorno)
        if area < area_imagen * _FRACCION_AREA_MINIMA_CONTORNO:
            break
        perimetro = cv2.arcLength(contorno, True)
        for tolerancia in (0.02, 0.03, 0.05):
            aproximado = cv2.approxPolyDP(contorno, tolerancia * perimetro, True)
            if len(aproximado) == 4 and cv2.isContourConvex(aproximado):
                candidatos.append(aproximado.reshape(4, 2))
                break
        else:
            rectangulo = cv2.minAreaRect(contorno)
            ancho, alto = rectangulo[1]
            if ancho > 0 and alto > 0 and area / (ancho * alto) >= _RELLENO_MINIMO_RECTANGULO:
                candidatos.append(cv2.boxPoints(rectangulo).astype(np.int32))
    return candidatos


def _ocupa_toda_la_imagen(contorno: Matriz, imagen: Matriz) -> bool:
    """Si el contorno es el borde de una imagen ya recortada: casi toda el area, o pegado al marco."""
    alto, ancho = imagen.shape[:2]
    area_imagen = float(alto * ancho)
    if cv2.contourArea(contorno.astype(np.float32)) >= area_imagen * _FRACCION_AREA_RECORTADA:
        return True
    margen_x, margen_y = ancho * _MARGEN_DEL_MARCO, alto * _MARGEN_DEL_MARCO
    return bool(
        any(x <= margen_x or x >= ancho - margen_x or y <= margen_y or y >= alto - margen_y for x, y in contorno)
    )


def _como_documento_recortado(imagen: Matriz) -> DocumentoProcesado | None:
    """Trata la imagen entera como el documento si ya viene recortada al borde.

    Es el caso de un DNI escaneado o de una foto que el cliente recorto antes de
    subirla: no hay fondo y por eso no hay contorno, pero la imagen misma tiene la
    proporcion de la tarjeta. Cualquier otra imagen sin contorno sigue sin ser un
    documento.
    """
    alto, ancho = imagen.shape[:2]
    if alto == 0 or ancho == 0:
        return None
    proporcion = max(ancho, alto) / min(ancho, alto)
    if abs(proporcion - _PROPORCION_ID1) > _TOLERANCIA_ID1:
        return None
    esquinas = np.array([[0, 0], [ancho - 1, 0], [ancho - 1, alto - 1], [0, alto - 1]], dtype=np.float32)
    return DocumentoProcesado(
        imagen_original=imagen, contorno=esquinas, imagen_enderezada=imagen, recortada_al_borde=True
    )


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
