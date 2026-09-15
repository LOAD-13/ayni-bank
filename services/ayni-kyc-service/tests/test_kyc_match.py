from unittest.mock import patch

import pytest
from fastapi.testclient import TestClient

from src.domain.model.resultado_verificacion import DecisionVerificacion
from src.infrastructure.vision.cotejo_facial import distancia_a_similitud_porcentaje
from src.infrastructure.web.main import app

client = TestClient(app)

def test_distancia_a_similitud_porcentaje_en_los_bordes():
    umbral = 0.68
    # Coincidencia perfecta.
    assert distancia_a_similitud_porcentaje(0.0, umbral) == 100.0
    # Justo en el umbral de DeepFace: ni aprueba sola ni rechaza, cae en revision manual.
    assert distancia_a_similitud_porcentaje(umbral, umbral) == 75.0
    # Al doble del umbral, y mas alla, ya no hay parecido que valga.
    assert distancia_a_similitud_porcentaje(umbral * 2, umbral) == 0.0
    assert distancia_a_similitud_porcentaje(umbral * 5, umbral) == 0.0

def test_distancia_a_similitud_porcentaje_respeta_el_umbral_del_modelo():
    # Un umbral distinto (otro modelo o metrica) desplaza toda la curva con el, no queda
    # fijo en 0.68: a mitad de camino de CUALQUIER umbral corresponde 87.5%.
    assert distancia_a_similitud_porcentaje(0.2, 0.4) == 87.5

@pytest.fixture
def dummy_image_bytes():
    # A valid but minimal 1x1 black image in PNG format
    return (
        b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01"
        b"\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\rIDATx\x9cc\xf8\xff"
        b"\xff?\x00\x05\xfe\x02\xfe\xa7\x35\x81\x84\x00\x00\x00\x00IEND\xaeB`\x82"
    )

@pytest.fixture
def mock_deepface():
    with patch('src.infrastructure.vision.cotejo_facial.DeepFace.verify') as mock:
        yield mock

def test_verify_match_approved(dummy_image_bytes, mock_deepface):
    # Sin "threshold" en el mock: cae al de reserva (0.68, el de VGG-Face + coseno).
    # 0.05 esta muy por debajo del umbral -> 100 - (0.05/0.68)*25 = 98.16%
    mock_deepface.return_value = {"distance": 0.05}

    files = {
        'selfie': ('selfie.png', dummy_image_bytes, 'image/png'),
        'documento': ('doc.png', dummy_image_bytes, 'image/png')
    }

    response = client.post("/kyc/verify-match", files=files)
    assert response.status_code == 200
    data = response.json()
    assert data["decision"] == DecisionVerificacion.APROBADA.value
    assert data["similitud"] == 98.16

def test_verify_match_manual_review(dummy_image_bytes, mock_deepface):
    # 0.5 es el orden de magnitud que dio una comparacion real de la misma persona
    # (selfie y DNI autenticos) en pruebas manuales: por debajo del umbral de DeepFace
    # (0.68, "es la misma persona") pero no tan cerca de 0 como para aprobar sola.
    # 100 - (0.5/0.68)*25 = 81.62%
    mock_deepface.return_value = {"distance": 0.5}

    files = {
        'selfie': ('selfie.png', dummy_image_bytes, 'image/png'),
        'documento': ('doc.png', dummy_image_bytes, 'image/png')
    }

    response = client.post("/kyc/verify-match", files=files)
    assert response.status_code == 200
    data = response.json()
    assert data["decision"] == DecisionVerificacion.REVISION_MANUAL.value
    assert data["similitud"] == 81.62

def test_verify_match_rejected(dummy_image_bytes, mock_deepface):
    # 0.8 supera el umbral de DeepFace (0.68): 75 - ((0.8-0.68)/(1.36-0.68))*75 = 61.76%
    mock_deepface.return_value = {"distance": 0.8}

    files = {
        'selfie': ('selfie.png', dummy_image_bytes, 'image/png'),
        'documento': ('doc.png', dummy_image_bytes, 'image/png')
    }

    response = client.post("/kyc/verify-match", files=files)
    assert response.status_code == 200
    data = response.json()
    assert data["decision"] == DecisionVerificacion.RECHAZADA.value
    assert data["similitud"] == 61.76

def test_verify_match_deepface_exception(dummy_image_bytes, mock_deepface):
    mock_deepface.side_effect = Exception("Face not found")
    
    files = {
        'selfie': ('selfie.png', dummy_image_bytes, 'image/png'),
        'documento': ('doc.png', dummy_image_bytes, 'image/png')
    }
    
    response = client.post("/kyc/verify-match", files=files)
    assert response.status_code == 200
    data = response.json()
    assert data["decision"] == DecisionVerificacion.RECHAZADA.value
    assert data["similitud"] == 0.0

def test_verify_match_invalid_image(mock_deepface):
    # Enviar un archivo que no es una imagen válida
    files = {
        'selfie': ('selfie.txt', b'hola', 'text/plain'),
        'documento': ('doc.txt', b'hola', 'text/plain')
    }
    
    response = client.post("/kyc/verify-match", files=files)
    assert response.status_code == 400
    assert "Error al procesar imágenes" in response.json()["detail"]
