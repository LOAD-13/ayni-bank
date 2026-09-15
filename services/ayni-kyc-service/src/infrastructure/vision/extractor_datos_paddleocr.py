"""OCR de anverso y reverso del DNI con PaddleOCR (subtarea 5 de AYNI-13).

Estrategia (ver ADR-0015): se intenta primero leer el MRZ del reverso, que
trae digitos verificadores y permite CONFIRMAR que el OCR leyo bien. Si el
MRZ no se encuentra o sus checksums no validan, se cae a heuristicas de
texto libre sobre el anverso (menos confiable, sin forma de validar).

Reutiliza procesamiento_documento (subtareas 3-4) para encontrar y enderezar
cada cara antes de pasarla al motor de OCR.
"""
from typing import Any, Protocol

from numpy.typing import NDArray
from paddleocr import PaddleOCR

from src.domain.model.resultado_verificacion import DatosIdentidadExtraidos, FuenteDatosIdentidad
from src.domain.port.verificador_identidad import AlmacenObjetosPort
from src.infrastructure.vision.heuristicas_anverso import extraer_por_heuristicas
from src.infrastructure.vision.mrz_td1 import encontrar_lineas_mrz, parsear_mrz
from src.infrastructure.vision.procesamiento_documento import procesar_documento

Matriz = NDArray[Any]


class MotorOcrPort(Protocol):
    """Aisla la libreria externa de OCR del resto del codigo (testeable con un fake)."""

    def reconocer_texto(self, imagen: Matriz) -> list[str]: ...


class MotorOcrPaddleOCR:
    """Envoltorio delgado sobre PaddleOCR."""

    def __init__(self) -> None:
        # enable_mkldnn=False: el backend oneDNN de PaddlePaddle en CPU falla
        # con NotImplementedError en algunos entornos (confirmado en
        # desarrollo local, Windows/x86_64 sin GPU). Desactivarlo cuesta algo
        # de rendimiento pero es mas importante que el servicio no falle.
        self._ocr = PaddleOCR(
            use_doc_orientation_classify=False,
            use_doc_unwarping=False,
            use_textline_orientation=False,
            enable_mkldnn=False,
        )

    def reconocer_texto(self, imagen: Matriz) -> list[str]:
        resultados = self._ocr.predict(imagen)
        if not resultados:
            return []

        textos = resultados[0].get("rec_texts", [])
        return [str(texto) for texto in textos]


class ExtractorDatosPaddleOCR:
    """Implementa ExtractorDatosPort: MRZ del reverso, con fallback al anverso."""

    def __init__(self, almacen_objetos: AlmacenObjetosPort, motor_ocr: MotorOcrPort | None = None) -> None:
        self._almacen_objetos = almacen_objetos
        self._motor_ocr: MotorOcrPort = motor_ocr if motor_ocr is not None else MotorOcrPaddleOCR()

    def extraer(
        self, clave_objeto_anverso: str, clave_objeto_reverso: str
    ) -> DatosIdentidadExtraidos | None:
        datos_desde_mrz = self._extraer_desde_mrz(clave_objeto_reverso)
        if datos_desde_mrz is not None:
            return datos_desde_mrz

        return self._extraer_desde_anverso(clave_objeto_anverso)

    def _extraer_desde_mrz(self, clave_objeto_reverso: str) -> DatosIdentidadExtraidos | None:
        lineas_texto = self._texto_crudo_del_documento(clave_objeto_reverso)
        if lineas_texto is None:
            return None

        lineas_mrz = encontrar_lineas_mrz(lineas_texto)
        if lineas_mrz is None:
            return None

        datos_mrz = parsear_mrz(*lineas_mrz)
        if datos_mrz is None or not datos_mrz.todos_los_checksums_validos:
            return None

        return DatosIdentidadExtraidos(
            dni=datos_mrz.numero_documento,
            nombres=datos_mrz.nombres,
            apellidos=datos_mrz.apellidos,
            fecha_nacimiento=datos_mrz.fecha_nacimiento,
            sexo=datos_mrz.sexo,
            fuente=FuenteDatosIdentidad.MRZ,
            confiable=True,
        )

    def _extraer_desde_anverso(self, clave_objeto_anverso: str) -> DatosIdentidadExtraidos | None:
        lineas_texto = self._texto_crudo_del_documento(clave_objeto_anverso)
        if lineas_texto is None:
            return None

        return extraer_por_heuristicas(lineas_texto)

    def _texto_crudo_del_documento(self, clave_objeto: str) -> list[str] | None:
        imagen_bytes = self._almacen_objetos.descargar(clave_objeto)
        procesado = procesar_documento(imagen_bytes)
        if procesado is None:
            return None

        return self._motor_ocr.reconocer_texto(procesado.imagen_enderezada)
