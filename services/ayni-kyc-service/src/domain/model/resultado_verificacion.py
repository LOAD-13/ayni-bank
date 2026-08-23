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
