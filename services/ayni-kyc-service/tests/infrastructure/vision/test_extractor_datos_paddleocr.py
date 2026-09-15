"""Pruebas de orquestacion del extractor: sin FastAPI, sin red, sin disco.

Usa un MotorOcrFake en vez de PaddleOCR real: cargar el modelo real tarda
varios segundos y no es necesario para probar la logica de "MRZ primero,
heuristicas como fallback" (esa logica de OCR en si ya tiene sus propios
tests en test_mrz_td1.py y test_heuristicas_anverso.py).
"""
import cv2
import numpy as np

from src.domain.model.resultado_verificacion import FuenteDatosIdentidad
from src.infrastructure.vision.extractor_datos_paddleocr import ExtractorDatosPaddleOCR
from src.infrastructure.vision.mrz_td1 import _checksum


class AlmacenObjetosFake:
    def __init__(self, objetos: dict[str, bytes]) -> None:
        self._objetos = objetos

    def descargar(self, clave_objeto: str) -> bytes:
        return self._objetos[clave_objeto]

    def calcular_hash(self, clave_objeto: str) -> str:
        raise NotImplementedError("No usado en estas pruebas")


class MotorOcrFake:
    """Retorna respuestas predefinidas en el orden en que se le llama."""

    def __init__(self, respuestas: list[list[str]]) -> None:
        self._respuestas = list(respuestas)
        self._indice = 0

    def reconocer_texto(self, imagen: object) -> list[str]:
        respuesta = self._respuestas[self._indice]
        self._indice += 1
        return respuesta


def _imagen_documento_generica() -> bytes:
    """Rectangulo con textura, proporcion DNI, suficiente para que
    procesar_documento encuentre un contorno (el contenido real del texto
    no importa: lo "reconocido" lo controla MotorOcrFake, no esta imagen)."""
    lienzo = np.full((600, 600, 3), 220, dtype=np.uint8)
    ancho_rect, alto_rect = 340, 214
    x0 = (600 - ancho_rect) // 2
    y0 = (600 - alto_rect) // 2
    cv2.rectangle(lienzo, (x0, y0), (x0 + ancho_rect, y0 + alto_rect), (40, 40, 40), thickness=-1)
    for i in range(5):
        y = y0 + 30 + i * 30
        if y < y0 + alto_rect - 10:
            cv2.line(lienzo, (x0 + 20, y), (x0 + ancho_rect - 20, y), (210, 210, 210), thickness=2)
    ok, buffer = cv2.imencode(".png", lienzo)
    assert ok
    return bytes(buffer)


def _construir_mrz_valido() -> tuple[str, str, str]:
    numero_doc = "87654321<"
    chk_doc = _checksum(numero_doc)
    linea1 = f"I<PER{numero_doc}{chk_doc}" + "<" * 15

    fecha_nac, fecha_cad, sexo, nacionalidad = "950322", "300322", "M", "PER"
    chk_nac = _checksum(fecha_nac)
    chk_cad = _checksum(fecha_cad)
    opcional2 = "<" * 11
    campo_compuesto = linea1[5:30] + fecha_nac + str(chk_nac) + fecha_cad + str(chk_cad) + opcional2
    chk_compuesto = _checksum(campo_compuesto)
    linea2 = f"{fecha_nac}{chk_nac}{sexo}{fecha_cad}{chk_cad}{nacionalidad}{opcional2}{chk_compuesto}"

    nombre_apellido = "GARCIA<LOPEZ<<MARIA<JOSE"
    linea3 = nombre_apellido + "<" * (30 - len(nombre_apellido))

    return linea1, linea2, linea3


def test_debe_usar_los_datos_del_mrz_cuando_el_reverso_se_lee_bien() -> None:
    # Dado
    imagen = _imagen_documento_generica()
    almacen = AlmacenObjetosFake({"anverso": imagen, "reverso": imagen})
    linea1, linea2, linea3 = _construir_mrz_valido()
    motor = MotorOcrFake(respuestas=[["REPUBLICA DEL PERU", linea1, linea2, linea3, "RENIEC"]])
    extractor = ExtractorDatosPaddleOCR(almacen, motor_ocr=motor)

    # Cuando
    resultado = extractor.extraer("anverso", "reverso")

    # Entonces
    assert resultado is not None
    assert resultado.dni == "87654321"
    assert resultado.apellidos == "GARCIA LOPEZ"
    assert resultado.nombres == "MARIA JOSE"
    assert resultado.fuente is FuenteDatosIdentidad.MRZ
    assert resultado.confiable is True


def test_debe_caer_a_heuristicas_del_anverso_cuando_el_reverso_no_tiene_mrz() -> None:
    # Dado: el "reverso" no trae MRZ reconocible, el "anverso" si trae las etiquetas
    imagen = _imagen_documento_generica()
    almacen = AlmacenObjetosFake({"anverso": imagen, "reverso": imagen})
    motor = MotorOcrFake(
        respuestas=[
            ["REPUBLICA DEL PERU", "RENIEC"],  # reverso: sin MRZ
            [
                "APELLIDOS", "GARCIA LOPEZ", "NOMBRES", "MARIA JOSE",
                "SEXO", "F", "FECHA DE NACIMIENTO", "22/03/1995", "87654321",
            ],  # anverso: heuristicas
        ]
    )
    extractor = ExtractorDatosPaddleOCR(almacen, motor_ocr=motor)

    # Cuando
    resultado = extractor.extraer("anverso", "reverso")

    # Entonces
    assert resultado is not None
    assert resultado.dni == "87654321"
    assert resultado.fuente is FuenteDatosIdentidad.HEURISTICA_ANVERSO
    assert resultado.confiable is False


def test_debe_caer_a_heuristicas_cuando_el_mrz_tiene_un_checksum_invalido() -> None:
    # Dado: MRZ presente pero con un caracter mal leido (checksum no coincide)
    imagen = _imagen_documento_generica()
    almacen = AlmacenObjetosFake({"anverso": imagen, "reverso": imagen})
    linea1, linea2, linea3 = _construir_mrz_valido()
    linea1_con_error = linea1.replace("87654321", "87654361")
    motor = MotorOcrFake(
        respuestas=[
            [linea1_con_error, linea2, linea3],  # reverso: MRZ con checksum invalido
            [
                "APELLIDOS", "GARCIA LOPEZ", "NOMBRES", "MARIA JOSE",
                "SEXO", "F", "FECHA DE NACIMIENTO", "22/03/1995", "11111111",
            ],
        ]
    )
    extractor = ExtractorDatosPaddleOCR(almacen, motor_ocr=motor)

    # Cuando
    resultado = extractor.extraer("anverso", "reverso")

    # Entonces: se descarta el MRZ (no confiable) y se usa el fallback
    assert resultado is not None
    assert resultado.fuente is FuenteDatosIdentidad.HEURISTICA_ANVERSO
    assert resultado.dni == "11111111"


def test_debe_retornar_none_cuando_ni_mrz_ni_heuristicas_encuentran_datos() -> None:
    # Dado
    imagen = _imagen_documento_generica()
    almacen = AlmacenObjetosFake({"anverso": imagen, "reverso": imagen})
    motor = MotorOcrFake(respuestas=[["texto irrelevante"], ["mas texto irrelevante"]])
    extractor = ExtractorDatosPaddleOCR(almacen, motor_ocr=motor)

    # Cuando / Entonces
    assert extractor.extraer("anverso", "reverso") is None
