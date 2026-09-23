import uuid
from typing import Annotated, Any, cast

import cv2
import numpy as np
from fastapi import APIRouter, File, HTTPException, UploadFile

from src.infrastructure.vision.cotejo_facial import cotejar_rostros

router = APIRouter(prefix="/kyc", tags=["KYC"])

MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024  # 5 MB por archivo

def validar_magic_bytes(contenido: bytes) -> bool:
    """Verifica que el archivo tenga firmas reconocidas (JPEG, PNG, WEBP, PDF)."""
    if len(contenido) < 4:
        return False
    return (
        contenido.startswith(b"\xff\xd8\xff")
        or contenido.startswith(b"\x89PNG")
        or (contenido.startswith(b"RIFF") and b"WEBP" in contenido[:12])
        or contenido.startswith(b"%PDF-")
    )

def load_image_from_bytes(file_bytes: bytes) -> np.ndarray[Any, Any]:
    nparr = np.frombuffer(file_bytes, np.uint8)
    img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("No se pudo decodificar la imagen enviada")
    return cast(np.ndarray[Any, Any], img)

@router.post("/verify-match", response_model=dict[str, Any])
async def verify_match(
    selfie: Annotated[UploadFile, File()],
    documento: Annotated[UploadFile, File()],
) -> dict[str, Any]:
    try:
        selfie_bytes = await selfie.read()
        doc_bytes = await documento.read()

        if len(selfie_bytes) > MAX_FILE_SIZE_BYTES or len(doc_bytes) > MAX_FILE_SIZE_BYTES:
            raise HTTPException(
                status_code=400,
                detail="El tamaño del archivo excede el límite permitido de 5 MB por imagen.",
            )

        if not validar_magic_bytes(selfie_bytes) or not validar_magic_bytes(doc_bytes):
            raise HTTPException(
                status_code=400,
                detail="Error al procesar imágenes: el formato del archivo no es válido (se requiere JPEG, PNG, WEBP o PDF).",
            )

        img_selfie = load_image_from_bytes(selfie_bytes)
        img_doc = load_image_from_bytes(doc_bytes)
    except HTTPException:
        raise
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
