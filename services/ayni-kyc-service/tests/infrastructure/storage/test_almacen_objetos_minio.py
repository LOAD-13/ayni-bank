"""Pruebas del adaptador de MinIO: sin red real (el cliente Minio se mockea).

get_object SI hace una llamada de red real a MinIO (a diferencia de la firma
de URLs pre-firmadas en Java, que es local): por eso aqui se mockea el
cliente en vez de instanciar uno real, en lugar de requerir un MinIO real
corriendo para poder ejecutar la suite de tests.
"""
import contextlib
import hashlib
from unittest.mock import MagicMock, patch

from src.infrastructure.storage.almacen_objetos_minio import AlmacenObjetosMinIO


def _construir_almacen_con_cliente_falso() -> tuple[AlmacenObjetosMinIO, MagicMock]:
    with patch("src.infrastructure.storage.almacen_objetos_minio.Minio") as minio_clase:
        cliente_falso = MagicMock()
        minio_clase.return_value = cliente_falso
        almacen = AlmacenObjetosMinIO(
            endpoint="http://minio:9000",
            access_key="ayni_minio",
            secret_key="cambiar_en_local",
            bucket="ayni-kyc-documentos",
        )
    return almacen, cliente_falso


def test_debe_construir_el_cliente_sin_esquema_y_marcando_http_como_inseguro() -> None:
    with patch("src.infrastructure.storage.almacen_objetos_minio.Minio") as minio_clase:
        AlmacenObjetosMinIO(
            endpoint="http://minio:9000",
            access_key="ayni_minio",
            secret_key="cambiar_en_local",
            bucket="ayni-kyc-documentos",
        )

    minio_clase.assert_called_once_with(
        "minio:9000", access_key="ayni_minio", secret_key="cambiar_en_local", secure=False
    )


def test_debe_marcar_https_como_seguro() -> None:
    with patch("src.infrastructure.storage.almacen_objetos_minio.Minio") as minio_clase:
        AlmacenObjetosMinIO(
            endpoint="https://minio.ayni.pe",
            access_key="k",
            secret_key="s",
            bucket="b",
        )

    _, kwargs = minio_clase.call_args
    assert kwargs["secure"] is True


def test_debe_descargar_los_bytes_del_objeto() -> None:
    almacen, cliente_falso = _construir_almacen_con_cliente_falso()
    respuesta_falsa = MagicMock()
    respuesta_falsa.read.return_value = b"contenido-del-documento"
    cliente_falso.get_object.return_value = respuesta_falsa

    resultado = almacen.descargar("kyc/abc/anverso-x.jpg")

    assert resultado == b"contenido-del-documento"
    cliente_falso.get_object.assert_called_once_with("ayni-kyc-documentos", "kyc/abc/anverso-x.jpg")


def test_debe_cerrar_y_liberar_la_conexion_tras_descargar() -> None:
    almacen, cliente_falso = _construir_almacen_con_cliente_falso()
    respuesta_falsa = MagicMock()
    respuesta_falsa.read.return_value = b"datos"
    cliente_falso.get_object.return_value = respuesta_falsa

    almacen.descargar("kyc/abc/reverso-x.jpg")

    respuesta_falsa.close.assert_called_once()
    respuesta_falsa.release_conn.assert_called_once()


def test_debe_cerrar_la_conexion_incluso_si_la_lectura_falla() -> None:
    almacen, cliente_falso = _construir_almacen_con_cliente_falso()
    respuesta_falsa = MagicMock()
    respuesta_falsa.read.side_effect = RuntimeError("conexion interrumpida")
    cliente_falso.get_object.return_value = respuesta_falsa

    with contextlib.suppress(RuntimeError):
        almacen.descargar("kyc/abc/reverso-x.jpg")

    respuesta_falsa.close.assert_called_once()
    respuesta_falsa.release_conn.assert_called_once()


def test_debe_calcular_el_hash_sha256_de_los_bytes_descargados() -> None:
    almacen, cliente_falso = _construir_almacen_con_cliente_falso()
    contenido = b"contenido-del-documento"
    respuesta_falsa = MagicMock()
    respuesta_falsa.read.return_value = contenido
    cliente_falso.get_object.return_value = respuesta_falsa

    resultado = almacen.calcular_hash("kyc/abc/anverso-x.jpg")

    assert resultado == hashlib.sha256(contenido).hexdigest()
