from typing import Any

import numpy as np
from deepface import DeepFace

from src.domain.model.resultado_verificacion import DecisionVerificacion, ResultadoCotejoFacial
from src.infrastructure.auditoria import log_audit_event
from src.infrastructure.config import settings


def determinar_decision(similitud_porcentaje: float) -> DecisionVerificacion:
    if similitud_porcentaje >= settings.KYC_MATCH_THRESHOLD:
        return DecisionVerificacion.APROBADA
    if similitud_porcentaje >= settings.KYC_MANUAL_REVIEW_THRESHOLD:
        return DecisionVerificacion.REVISION_MANUAL
    return DecisionVerificacion.RECHAZADA

# Umbral de reserva si el modelo no lo informa (no deberia pasar: DeepFace.verify() siempre
# lo devuelve). Es el propio umbral de VGG-Face + coseno que trae DeepFace: no es un numero
# inventado aqui, es el punto que la libreria ya valido como frontera entre "es la misma
# persona" y "no lo es".
UMBRAL_DE_RESERVA = 0.68

def distancia_a_similitud_porcentaje(distancia: float, umbral: float) -> float:
    """Convierte una distancia de DeepFace en un porcentaje de 0 a 100.

    Antes esto era `(1 - distancia) * 100`: tratar la distancia coseno cruda como un
    porcentaje directo. El problema es que esa distancia no se mueve en un rango simetrico
    de 0 a 1 entre "misma persona" y "personas distintas" - para VGG-Face + coseno, DeepFace
    considera que hay coincidencia con distancias de hasta 0.68, bastante lejos de 0. Con la
    formula vieja, dos fotos de la misma persona con una distancia de 0.49 (caso real,
    verificado en pruebas manuales con selfie y DNI autenticos) sacaban ~51% y el sistema
    rechazaba a alguien que si era quien decia ser.

    Esta version usa el propio umbral de DeepFace como bisagra: una distancia de 0 es 100%,
    el umbral exacto es 75% (el piso de revision manual, no de rechazo automatico - llegar
    justo al limite de lo que el modelo llama "coincidencia" no deberia bastar para aprobar
    solo, pero tampoco para rechazar de plano), y de ahi sigue cayendo hasta 0% al doble del
    umbral, que es donde para cualquier modelo razonable ya no hay parecido.
    """
    techo = umbral * 2
    if distancia <= umbral:
        return 100.0 - (distancia / umbral) * 25.0
    exceso = min(distancia, techo) - umbral
    return max(0.0, 75.0 - (exceso / (techo - umbral)) * 75.0)

def cotejar_rostros(
    imagen1: np.ndarray[Any, Any],
    imagen2: np.ndarray[Any, Any],
    id_transaccion: str,
) -> ResultadoCotejoFacial:
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

        distancia = float(resultado.get("distance", 1.0))
        umbral = float(resultado.get("threshold", UMBRAL_DE_RESERVA))
        similitud_porcentaje = distancia_a_similitud_porcentaje(distancia, umbral)

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
