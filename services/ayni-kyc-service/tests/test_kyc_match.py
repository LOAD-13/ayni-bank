import sys
from unittest.mock import MagicMock, patch

# Mockear deepface en sys.modules si no está instalado en el entorno local
if "deepface" not in sys.modules:
    mock_df_module = MagicMock()
    sys.modules["deepface"] = mock_df_module

import cv2
import numpy as np
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
def dummy_image_bytes() -> bytes:
    """PNG valido de 8x8 en negro, generado por OpenCV."""
    ok, buffer = cv2.imencode(".png", np.zeros((8, 8, 3), dtype=np.uint8))
    assert ok, "OpenCV no pudo codificar la imagen de prueba"
    return bytes(buffer.tobytes())

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
    # Enviar un archivo que no es una imagen válida (magic bytes fallan)
    files = {
        'selfie': ('selfie.txt', b'hola', 'text/plain'),
        'documento': ('doc.txt', b'hola', 'text/plain')
    }
    
    response = client.post("/kyc/verify-match", files=files)
    assert response.status_code == 400
    assert "El formato del archivo no es una imagen válida" in response.json()["detail"]

def test_verify_match_file_too_large(dummy_image_bytes, mock_deepface):
    # Enviar archivo que supera los 5MB
    large_bytes = b"\xff\xd8\xff\xe0" + (b"0" * (5 * 1024 * 1024 + 10))
    files = {
        'selfie': ('selfie.jpg', large_bytes, 'image/jpeg'),
        'documento': ('doc.png', dummy_image_bytes, 'image/png')
    }

    response = client.post("/kyc/verify-match", files=files)
    assert response.status_code == 400
    assert "excede el límite permitido" in response.json()["detail"]

