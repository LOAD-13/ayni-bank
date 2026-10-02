"""Acceso a los documentos KYC en MinIO (AYNI-13 subtarea 8).

Las imagenes nunca viajan por la API: Java pasa solo la clave del objeto,
y este adaptador la lee directamente de MinIO. Ver diseno-base.md §4.1.
"""
import hashlib
from urllib.parse import urlparse

from minio import Minio
from minio.error import S3Error

from src.domain.port.verificador_identidad import ObjetoNoEncontradoError


class AlmacenObjetosMinIO:
    """Implementa AlmacenObjetosPort contra un servidor MinIO real."""

    def __init__(self, endpoint: str, access_key: str, secret_key: str, bucket: str) -> None:
        partes = urlparse(endpoint)
        host = partes.netloc or partes.path
        self._cliente = Minio(
            host,
            access_key=access_key,
            secret_key=secret_key,
            secure=partes.scheme == "https",
        )
        self._bucket = bucket

    def descargar(self, clave_objeto: str) -> bytes:
        try:
            respuesta = self._cliente.get_object(self._bucket, clave_objeto)
        except S3Error as error:
            if error.code == "NoSuchKey":
                raise ObjetoNoEncontradoError(clave_objeto) from error
            raise
        try:
            return respuesta.read()
        finally:
            respuesta.close()
            respuesta.release_conn()

    def calcular_hash(self, clave_objeto: str) -> str:
        """SHA-256 en hexadecimal. Java lo persiste en documento_kyc.hash_sha256
        (subtarea 9) para detectar alteracion del objeto (diseno-base.md §4.1)."""
        return hashlib.sha256(self.descargar(clave_objeto)).hexdigest()
