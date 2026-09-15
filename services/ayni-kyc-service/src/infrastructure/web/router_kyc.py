import uuid
from typing import Annotated, Any, cast

import cv2
import numpy as np
from fastapi import APIRouter, File, HTTPException, UploadFile

from src.infrastructure.vision.cotejo_facial import cotejar_rostros

router = APIRouter(prefix="/kyc", tags=["KYC"])

def load_image_from_bytes(file_bytes: bytes) -> np.ndarray[Any, Any]:
    nparr = np.frombuffer(file_bytes, np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Could not decode image")
    # cv2 no publica stubs completos, asi que imdecode devuelve Any. El cast deja
    # constancia del tipo real y evita que mypy --strict lo propague al resto.
    return cast(np.ndarray[Any, Any], img)

@router.post("/verify-match", response_model=dict[str, Any])
async def verify_match(
    selfie: Annotated[UploadFile, File()],
    documento: Annotated[UploadFile, File()],
) -> dict[str, Any]:
    try:
        selfie_bytes = await selfie.read()
        doc_bytes = await documento.read()

        img_selfie = load_image_from_bytes(selfie_bytes)
        img_doc = load_image_from_bytes(doc_bytes)
    except Exception as e:
        raise HTTPException(
            status_code=400, detail=f"Error al procesar imágenes: {e!s}"
        ) from e


    id_transaccion = str(uuid.uuid4())
    resultado = cotejar_rostros(img_selfie, img_doc, id_transaccion)
    
    return {
        "id_transaccion": id_transaccion,
        "similitud": round(resultado.similitud * 100.0, 2),
        "decision": resultado.decision.value,
        "supero_vivacidad": resultado.supero_vivacidad
    }
