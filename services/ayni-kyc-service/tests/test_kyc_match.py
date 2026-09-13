import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch
import numpy as np

from src.infrastructure.web.main import app
from src.domain.model.resultado_verificacion import DecisionVerificacion
from src.infrastructure.config import settings

client = TestClient(app)

@pytest.fixture
def dummy_image_bytes():
    # A valid but minimal 1x1 black image in PNG format
    return b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15\xc4\x89\x00\x00\x00\rIDATx\x9cc\xf8\xff\xff?\x00\x05\xfe\x02\xfe\xa7\x35\x81\x84\x00\x00\x00\x00IEND\xaeB`\x82'

@pytest.fixture
def mock_deepface():
    with patch('src.infrastructure.vision.cotejo_facial.DeepFace.verify') as mock:
        yield mock

def test_verify_match_approved(dummy_image_bytes, mock_deepface):
    mock_deepface.return_value = {"distance": 0.05} # Similitud 95%
    
    files = {
        'selfie': ('selfie.png', dummy_image_bytes, 'image/png'),
        'documento': ('doc.png', dummy_image_bytes, 'image/png')
    }
    
    response = client.post("/kyc/verify-match", files=files)
    assert response.status_code == 200
    data = response.json()
    assert data["decision"] == DecisionVerificacion.APROBADA.value
    assert data["similitud"] == 95.0

def test_verify_match_manual_review(dummy_image_bytes, mock_deepface):
    mock_deepface.return_value = {"distance": 0.15} # Similitud 85%
    
    files = {
        'selfie': ('selfie.png', dummy_image_bytes, 'image/png'),
        'documento': ('doc.png', dummy_image_bytes, 'image/png')
    }
    
    response = client.post("/kyc/verify-match", files=files)
    assert response.status_code == 200
    data = response.json()
    assert data["decision"] == DecisionVerificacion.REVISION_MANUAL.value
    assert data["similitud"] == 85.0

def test_verify_match_rejected(dummy_image_bytes, mock_deepface):
    mock_deepface.return_value = {"distance": 0.8} # Similitud 60%
    
    files = {
        'selfie': ('selfie.png', dummy_image_bytes, 'image/png'),
        'documento': ('doc.png', dummy_image_bytes, 'image/png')
    }
    
    response = client.post("/kyc/verify-match", files=files)
    assert response.status_code == 200
    data = response.json()
    assert data["decision"] == DecisionVerificacion.RECHAZADA.value
    assert data["similitud"] == 60.0

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
