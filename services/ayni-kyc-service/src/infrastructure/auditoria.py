import json
import logging
from datetime import UTC, datetime
from typing import Any

logger = logging.getLogger("ayni_kyc_audit")
logger.setLevel(logging.INFO)

if not logger.handlers:
    handler = logging.StreamHandler()
    formatter = logging.Formatter('%(message)s')
    handler.setFormatter(formatter)
    logger.addHandler(handler)

def log_audit_event(event_name: str, payload: dict[str, Any]) -> None:
    """Registra un evento de auditoría en formato JSON estructurado."""
    log_entry = {
        # utcnow() devuelve una fecha SIN zona horaria a la que luego se le pega
        # una "Z" que afirma ser UTC: una pista de auditoria no puede mentir
        # sobre su propio instante. now(UTC) es consciente de la zona.
        "timestamp": datetime.now(UTC).isoformat(),
        "event_name": event_name,
        "payload": payload
    }
    logger.info(json.dumps(log_entry))
