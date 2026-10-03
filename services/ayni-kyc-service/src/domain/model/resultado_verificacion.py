"""Modelo de dominio del servicio de verificacion de identidad.

Sin dependencias de FastAPI ni de ninguna libreria de vision: el dominio
define QUE se verifica, no COMO se implementa.
"""
from dataclasses import dataclass
from datetime import date
from enum import Enum


class DecisionVerificacion(str, Enum):
    APROBADA = "APROBADA"
    RECHAZADA = "RECHAZADA"
    REVISION_MANUAL = "REVISION_MANUAL"


@dataclass(frozen=True)
class ResultadoCotejoFacial:
    """Resultado del cotejo entre la selfie y la foto del documento."""

    similitud: float
    supero_vivacidad: bool
    decision: DecisionVerificacion

    def __post_init__(self) -> None:
        if not 0.0 <= self.similitud <= 1.0:
            raise ValueError("La similitud debe estar entre 0.0 y 1.0")


@dataclass(frozen=True)
class ResultadoValidacionCalidad:
    """Resultado de validar nitidez, reflejos y encuadre de una foto de documento.

    Distingue cual de los cuatro controles fallo: el escenario 3 de HU-02 pide
    decirle al solicitante el motivo concreto (ver ResultadoEvaluacionCaptura).
    """

    es_nitida: bool
    sin_reflejos: bool
    bien_encuadrada: bool
    bien_iluminada: bool

    @property
    def es_valida(self) -> bool:
        return self.es_nitida and self.sin_reflejos and self.bien_encuadrada and self.bien_iluminada


class MotivoRechazoCaptura(str, Enum):
    """Por que se rechaza una foto, para que el solicitante sepa que repetir (escenarios 2 y 3)."""

    NO_ES_DNI = "NO_ES_DNI"
    ENCUADRE = "ENCUADRE"
    DESENFOQUE = "DESENFOQUE"
    REFLEJO = "REFLEJO"
    ILUMINACION = "ILUMINACION"
    # Se pidio el reverso y la foto es del anverso (tiene el rostro del titular).
    LADO_INCORRECTO = "LADO_INCORRECTO"


@dataclass(frozen=True)
class ResultadoEvaluacionCaptura:
    """Resultado de evaluar una foto de un lado del DNI antes de aceptarla.

    Si hay varios problemas se informa uno solo, el que conviene corregir
    primero: un documento cortado hace poco fiables las demas medidas, y un
    desenfoque suele arrastrar tambien a la iluminacion.

    Cuando no se encontro ningun documento en la foto, antes de decir "no es un
    DNI" se mira si la foto esta borrosa u oscura: el documento puede estar ahi
    y lo que hay que repetir es la toma. `calidad` es None cuando no hay nada
    que medir: los bytes no son una imagen, o se encontro un objeto nitido que
    no tiene la forma de un DNI.
    """

    es_dni: bool
    calidad: ResultadoValidacionCalidad | None
    lado_incorrecto: bool = False

    @property
    def motivo_rechazo(self) -> MotivoRechazoCaptura | None:
        if self.calidad is None:
            return MotivoRechazoCaptura.NO_ES_DNI
        if not self.es_dni:
            return self._motivo_sin_documento(self.calidad)
        if self.lado_incorrecto:
            return MotivoRechazoCaptura.LADO_INCORRECTO
        if not self.calidad.bien_encuadrada:
            return MotivoRechazoCaptura.ENCUADRE
        if not self.calidad.es_nitida:
            return MotivoRechazoCaptura.DESENFOQUE
        if not self.calidad.sin_reflejos:
            return MotivoRechazoCaptura.REFLEJO
        if not self.calidad.bien_iluminada:
            return MotivoRechazoCaptura.ILUMINACION
        return None

    @staticmethod
    def _motivo_sin_documento(calidad: ResultadoValidacionCalidad) -> MotivoRechazoCaptura:
        if not calidad.es_nitida:
            return MotivoRechazoCaptura.DESENFOQUE
        if not calidad.bien_iluminada:
            return MotivoRechazoCaptura.ILUMINACION
        return MotivoRechazoCaptura.NO_ES_DNI

    @property
    def aceptada(self) -> bool:
        return self.motivo_rechazo is None


class FuenteDatosIdentidad(str, Enum):
    """De donde salieron los datos: MRZ (confiable, con checksum) o heuristicas
    de texto libre sobre el anverso (fallback, sin forma de validar la lectura)."""

    MRZ = "MRZ"
    HEURISTICA_ANVERSO = "HEURISTICA_ANVERSO"


@dataclass(frozen=True)
class DatosIdentidadExtraidos:
    """Datos de identidad extraidos del DNI por OCR (subtarea 5 de AYNI-13).

    `confiable` distingue si vinieron del MRZ con checksums validos (se puede
    confiar en la lectura) o de heuristicas sobre el anverso (fallback sin
    forma de validar que el OCR leyo bien - ver ADR-0009 sobre lecturas
    plausibles pero erroneas).

    `fecha_emision` es opcional: el MRZ no la trae y se lee del anverso, donde
    el OCR puede no encontrarla. El titular la completa al confirmar sus datos.
    """

    dni: str
    nombres: str
    apellidos: str
    fecha_nacimiento: date
    sexo: str
    fuente: FuenteDatosIdentidad
    confiable: bool
    fecha_emision: date | None = None
