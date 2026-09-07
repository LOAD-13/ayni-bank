"""Modelo de dominio del servicio de verificacion de identidad.

Sin dependencias de FastAPI ni de ninguna libreria de vision: el dominio
define QUE se verifica, no COMO se implementa.
"""
from dataclasses import dataclass
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

    Distingue cual de los tres controles fallo (util para logs internos y
    para guiar al usuario en la subtarea 12), aunque el contrato publico
    solo exponga un motivo generico QUALITY_CHECK_FAILED.
    """

    es_nitida: bool
    sin_reflejos: bool
    bien_encuadrada: bool

    @property
    def es_valida(self) -> bool:
        return self.es_nitida and self.sin_reflejos and self.bien_encuadrada
