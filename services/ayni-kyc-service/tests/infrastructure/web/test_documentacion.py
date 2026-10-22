"""La documentacion de la API no se publica por defecto (ADR-0033)."""
from fastapi.testclient import TestClient

from src.infrastructure.config import Settings
from src.infrastructure.web.main import app


def test_kyc_api_docs_esta_apagado_por_defecto() -> None:
    assert Settings(_env_file=None).KYC_API_DOCS is False


def test_sin_kyc_api_docs_no_hay_documentacion_publicada() -> None:
    cliente = TestClient(app)
    for ruta in ("/docs", "/redoc", "/openapi.json"):
        assert cliente.get(ruta).status_code == 404, ruta
