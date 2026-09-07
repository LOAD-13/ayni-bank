"""Puertos del dominio. La implementacion vive en infrastructure."""
from typing import Protocol

from src.domain.model.resultado_verificacion import ResultadoCotejoFacial, ResultadoValidacionCalidad


class DetectorDocumentoPort(Protocol):
    """Determina si una imagen corresponde a un DNI peruano y extrae sus datos."""

    def es_documento_identidad(self, clave_objeto: str) -> bool: ...


class ValidadorCalidadPort(Protocol):
    """Valida nitidez, reflejos y encuadre de la foto de un documento."""

    def validar(self, clave_objeto: str) -> ResultadoValidacionCalidad: ...


class CotejadorFacialPort(Protocol):
    """Compara la selfie con la foto del documento."""

    def cotejar(self, clave_selfie: str, clave_documento: str) -> ResultadoCotejoFacial: ...


class AlmacenObjetosPort(Protocol):
    """Acceso al almacenamiento de objetos. Las imagenes nunca viajan por la API."""

    def descargar(self, clave_objeto: str) -> bytes: ...

    def calcular_hash(self, clave_objeto: str) -> str: ...
