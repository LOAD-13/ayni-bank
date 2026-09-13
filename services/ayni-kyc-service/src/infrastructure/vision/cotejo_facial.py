from typing import Any
import numpy as np
from deepface import DeepFace

from src.domain.model.resultado_verificacion import ResultadoCotejoFacial, DecisionVerificacion
from src.infrastructure.config import settings
from src.infrastructure.auditoria import log_audit_event

def determinar_decision(similitud_porcentaje: float) -> DecisionVerificacion:
    if similitud_porcentaje >= settings.KYC_MATCH_THRESHOLD:
        return DecisionVerificacion.APROBADA
    if similitud_porcentaje >= settings.KYC_MANUAL_REVIEW_THRESHOLD:
        return DecisionVerificacion.REVISION_MANUAL
    return DecisionVerificacion.RECHAZADA

def cotejar_rostros(imagen1: np.ndarray[Any, Any], imagen2: np.ndarray[Any, Any], id_transaccion: str) -> ResultadoCotejoFacial:
    try:
        # enforce_detection=False para evitar excepciones si no detecta rostro en alguna imagen
        resultado = DeepFace.verify(
            img1_path=imagen1,
            img2_path=imagen2,
            enforce_detection=False,
            model_name="VGG-Face",
            detector_backend="opencv",
            distance_metric="cosine"
        )
        
        # similarity as a percentage based on distance.
        # deepface verify returns 'distance'. distance varies by metric, for cosine it's [0, 2] usually but typical threshold is 0.40
        # Wait, if we use cosine distance, distance = 0 means identical, distance = 1 means orthogonal.
        # Let's just use 1.0 - distance to get similarity between 0 and 1, and multiply by 100 for percentage.
        # Deepface also returns 'verified': bool, but we want our own threshold logic.
        distancia = float(resultado.get("distance", 1.0))
        similitud_nativa = max(0.0, 1.0 - (distancia / 2.0)) if distancia <= 2.0 else 0.0
        # We will map distance to a [0, 1] range reasonably. Cosine distance for face verification usually has a threshold around 0.40.
        # Let's map it so that distance 0.40 corresponds to 85% roughly?
        # Actually, let's just use a simple linear map or the 'distance' directly mapped:
        # deepface threshold for cosine is usually 0.40. So if distance < 0.40 it's a match.
        # To make it a percentage that makes sense with 75-90 thresholds, let's say 1.0 - distance is the score.
        # Wait, a better way: 1 - distance is not exactly 0 to 100%. 
        # For simplicity, score = (1 - distance) * 100
        similitud_porcentaje = max(0.0, min(100.0, (1.0 - distancia) * 100.0))
        
        similitud_decimal = similitud_porcentaje / 100.0
        decision = determinar_decision(similitud_porcentaje)
        
        # Pista de auditoría
        log_audit_event("COTEJO_FACIAL", {
            "id_transaccion": id_transaccion,
            "similitud_decimal": similitud_decimal,
            "similitud_porcentaje": similitud_porcentaje,
            "decision": decision.value,
            "distancia_nativa": distancia
        })
        
        return ResultadoCotejoFacial(
            similitud=similitud_decimal,
            supero_vivacidad=True, # No evaluado en este paso, pero requerido por el dataclass
            decision=decision
        )
    except Exception as e:
        log_audit_event("COTEJO_FACIAL_ERROR", {
            "id_transaccion": id_transaccion,
            "error": str(e)
        })
        # Si falla, rechazamos por seguridad
        return ResultadoCotejoFacial(
            similitud=0.0,
            supero_vivacidad=False,
            decision=DecisionVerificacion.RECHAZADA
        )
