"""Pruebas de los endpoints de verificacion del DNI: sin MinIO ni PaddleOCR reales.

El almacen y el extractor se sustituyen con `dependency_overrides`; la
evaluacion usa las piezas reales de OpenCV sobre imagenes sinteticas, igual que
test_validador_calidad_opencv.py.
"""
from collections.abc import Iterator
from datetime import date

import cv2
import numpy as np
import pytest
from fastapi.testclient import TestClient
from numpy.typing import NDArray

from src.domain.model.resultado_verificacion import DatosIdentidadExtraidos, FuenteDatosIdentidad
from src.domain.port.verificador_identidad import ObjetoNoEncontradoError
from src.infrastructure.web.main import app
from src.infrastructure.web.router_documentos import obtener_almacen, obtener_extractor

SOLICITUD = "0f8fad5b-d9cb-469f-a165-70867728950e"
CLAVE_ANVERSO = f"kyc/{SOLICITUD}/anverso-7c9e6679-7425-40de-944b-e07fc1f90ae7.jpg"
CLAVE_REVERSO = f"kyc/{SOLICITUD}/reverso-6ba7b810-9dad-11d1-80b4-00c04fd430c8.png"


class AlmacenObjetosFake:
    def __init__(self, objetos: dict[str, bytes]) -> None:
        self._objetos = objetos

    def descargar(self, clave_objeto: str) -> bytes:
        if clave_objeto not in self._objetos:
            raise ObjetoNoEncontradoError(clave_objeto)
        return self._objetos[clave_objeto]

    def calcular_hash(self, clave_objeto: str) -> str:
        raise NotImplementedError("No usado en estas pruebas")


class ExtractorFake:
    def __init__(self, resultado: DatosIdentidadExtraidos | None) -> None:
        self._resultado = resultado
        self.llamadas: list[tuple[str, str]] = []

    def extraer(self, clave_objeto_anverso: str, clave_objeto_reverso: str) -> DatosIdentidadExtraidos | None:
        self.llamadas.append((clave_objeto_anverso, clave_objeto_reverso))
        return self._resultado


def _codificar(imagen: NDArray[np.uint8]) -> bytes:
    ok, buffer = cv2.imencode(".png", imagen)
    assert ok
    return bytes(buffer)


def _foto_de_documento(ancho_rect: int = 340, alto_rect: int = 214) -> NDArray[np.uint8]:
    """Rectangulo con textura sobre una mesa clara (mismo criterio que las pruebas del validador)."""
    lienzo = np.full((600, 600, 3), 220, dtype=np.uint8)
    x0, y0 = (600 - ancho_rect) // 2, (600 - alto_rect) // 2
    cv2.rectangle(lienzo, (x0, y0), (x0 + ancho_rect, y0 + alto_rect), (40, 40, 40), thickness=-1)
    for i in range(5):
        y = y0 + 30 + i * 30
        if y < y0 + alto_rect - 10:
            cv2.line(lienzo, (x0 + 20, y), (x0 + ancho_rect - 20, y), (210, 210, 210), thickness=2)
    return lienzo


@pytest.fixture
def cliente() -> Iterator[TestClient]:
    yield TestClient(app)
    app.dependency_overrides.clear()


def _con_almacen(objetos: dict[str, bytes]) -> None:
    app.dependency_overrides[obtener_almacen] = lambda: AlmacenObjetosFake(objetos)


# ─── Evaluacion de una captura ────────────────────────────────────────────


def test_debe_aceptar_una_foto_de_dni_nitida_y_bien_encuadrada(cliente: TestClient) -> None:
    _con_almacen({CLAVE_ANVERSO: _codificar(_foto_de_documento())})

    respuesta = cliente.post(
        "/kyc/documentos/evaluacion", json={"documentKey": CLAVE_ANVERSO, "lado": "ANVERSO"}
    )

    assert respuesta.status_code == 200
    cuerpo = respuesta.json()
    assert cuerpo["aceptada"] is True
    assert cuerpo["esDni"] is True
    assert cuerpo["motivoRechazo"] is None


def test_debe_rechazar_un_objeto_que_no_es_un_dni(cliente: TestClient) -> None:
    # Un cuadrado: proporcion 1.0, lejos del 1.586 del formato ID-1
    _con_almacen({CLAVE_ANVERSO: _codificar(_foto_de_documento(ancho_rect=250, alto_rect=250))})

    respuesta = cliente.post(
        "/kyc/documentos/evaluacion", json={"documentKey": CLAVE_ANVERSO, "lado": "ANVERSO"}
    )

    assert respuesta.status_code == 200
    assert respuesta.json()["aceptada"] is False
    assert respuesta.json()["motivoRechazo"] == "NO_ES_DNI"


def test_debe_rechazar_como_no_dni_unos_bytes_que_no_son_una_imagen(cliente: TestClient) -> None:
    _con_almacen({CLAVE_ANVERSO: b"%PDF-1.7 no es una foto"})

    respuesta = cliente.post(
        "/kyc/documentos/evaluacion", json={"documentKey": CLAVE_ANVERSO, "lado": "ANVERSO"}
    )

    assert respuesta.json()["motivoRechazo"] == "NO_ES_DNI"


def test_debe_informar_el_motivo_concreto_cuando_la_foto_esta_desenfocada(cliente: TestClient) -> None:
    borrosa = cv2.GaussianBlur(_foto_de_documento(), (25, 25), 0)
    _con_almacen({CLAVE_REVERSO: _codificar(borrosa)})

    respuesta = cliente.post(
        "/kyc/documentos/evaluacion", json={"documentKey": CLAVE_REVERSO, "lado": "REVERSO"}
    )

    assert respuesta.json()["aceptada"] is False
    assert respuesta.json()["motivoRechazo"] == "DESENFOQUE"


def test_debe_responder_404_cuando_la_clave_no_existe(cliente: TestClient) -> None:
    _con_almacen({})

    respuesta = cliente.post(
        "/kyc/documentos/evaluacion", json={"documentKey": CLAVE_ANVERSO, "lado": "ANVERSO"}
    )

    assert respuesta.status_code == 404
    assert respuesta.json()["code"] == "NOT_FOUND"


def test_debe_rechazar_con_400_las_imagenes_de_mas_de_5_mb(cliente: TestClient) -> None:
    _con_almacen({CLAVE_ANVERSO: b"\xff\xd8\xff" + b"0" * (5 * 1024 * 1024)})

    respuesta = cliente.post(
        "/kyc/documentos/evaluacion", json={"documentKey": CLAVE_ANVERSO, "lado": "ANVERSO"}
    )

    assert respuesta.status_code == 400


@pytest.mark.parametrize(
    "clave",
    [
        "kyc/../otro-bucket/secreto.jpg",
        f"kyc/{SOLICITUD}/selfie-7c9e6679-7425-40de-944b-e07fc1f90ae7.jpg",
        f"kyc/{SOLICITUD}/anverso-7c9e6679-7425-40de-944b-e07fc1f90ae7.pdf",
        "cualquier-cosa",
    ],
)
def test_debe_rechazar_con_400_las_claves_que_no_son_de_un_dni(cliente: TestClient, clave: str) -> None:
    _con_almacen({clave: _codificar(_foto_de_documento())})

    respuesta = cliente.post("/kyc/documentos/evaluacion", json={"documentKey": clave, "lado": "ANVERSO"})

    assert respuesta.status_code == 400
    assert respuesta.json()["code"] == "BAD_REQUEST"


# ─── Extraccion de datos ──────────────────────────────────────────────────


def test_debe_devolver_los_datos_extraidos_en_camel_case(cliente: TestClient) -> None:
    extractor = ExtractorFake(
        DatosIdentidadExtraidos(
            dni="44556677",
            nombres="ANA LUCIA",
            apellidos="QUISPE MAMANI",
            fecha_nacimiento=date(1990, 5, 15),
            sexo="F",
            fuente=FuenteDatosIdentidad.MRZ,
            confiable=True,
            fecha_emision=date(2021, 8, 20),
        )
    )
    app.dependency_overrides[obtener_extractor] = lambda: extractor

    respuesta = cliente.post(
        "/kyc/documentos/extraccion",
        json={"anversoDocumentKey": CLAVE_ANVERSO, "reversoDocumentKey": CLAVE_REVERSO},
    )

    assert respuesta.status_code == 200
    assert respuesta.json() == {
        "legible": True,
        "datos": {
            "dni": "44556677",
            "nombres": "ANA LUCIA",
            "apellidos": "QUISPE MAMANI",
            "fechaNacimiento": "1990-05-15",
            "sexo": "F",
            "fechaEmision": "2021-08-20",
            "fuente": "MRZ",
            "confiable": True,
        },
    }
    assert extractor.llamadas == [(CLAVE_ANVERSO, CLAVE_REVERSO)]


def test_debe_indicar_que_no_es_legible_cuando_el_ocr_no_encuentra_datos(cliente: TestClient) -> None:
    app.dependency_overrides[obtener_extractor] = lambda: ExtractorFake(None)

    respuesta = cliente.post(
        "/kyc/documentos/extraccion",
        json={"anversoDocumentKey": CLAVE_ANVERSO, "reversoDocumentKey": CLAVE_REVERSO},
    )

    assert respuesta.status_code == 200
    assert respuesta.json() == {"legible": False}


def test_si_la_precarga_del_ocr_falla_el_servicio_arranca_igual(monkeypatch: pytest.MonkeyPatch) -> None:
    # Dado: precarga activada y un OCR que no se puede cargar (p. ej. falta libgomp)
    from src.infrastructure.web import main

    def ocr_roto() -> None:
        raise ImportError("libgomp.so.1: cannot open shared object file")

    ocr_roto.cache_clear = lambda: None  # type: ignore[attr-defined]
    monkeypatch.setattr(main.settings, "KYC_PRECARGAR_OCR", True)
    monkeypatch.setattr(main, "obtener_extractor", ocr_roto)

    # Cuando: arranca la aplicacion (el `with` ejecuta el ciclo de vida)
    with TestClient(app) as cliente:
        respuesta = cliente.get("/health")

    # Entonces: sigue atendiendo
    assert respuesta.status_code == 200
