from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from typing import Any
import numpy as np
import cv2
import uuid

from src.domain.model.resultado_verificacion import ResultadoCotejoFacial
from src.infrastructure.vision.cotejo_facial import cotejar_rostros

router = APIRouter(prefix="/kyc", tags=["KYC"])

def load_image_from_bytes(file_bytes: bytes) -> np.ndarray[Any, Any]:
    nparr = np.frombuffer(file_bytes, np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Could not decode image")
    return img

@router.post("/verify-match", response_model=dict[str, Any])
async def verify_match(
    selfie: UploadFile = File(...),
    documento: UploadFile = File(...)
):
    try:
        selfie_bytes = await selfie.read()
        doc_bytes = await documento.read()
        
        img_selfie = load_image_from_bytes(selfie_bytes)
        img_doc = load_image_from_bytes(doc_bytes)
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Error al procesar imágenes: {str(e)}")
    
    id_transaccion = str(uuid.uuid4())
    resultado = cotejar_rostros(img_selfie, img_doc, id_transaccion)
    
    return {
        "id_transaccion": id_transaccion,
        "similitud": round(resultado.similitud * 100.0, 2),
        "decision": resultado.decision.value,
        "supero_vivacidad": resultado.supero_vivacidad
    }
