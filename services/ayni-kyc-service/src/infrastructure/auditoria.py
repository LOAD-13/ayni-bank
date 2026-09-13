import logging
import json
from datetime import datetime
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
        "timestamp": datetime.utcnow().isoformat() + "Z",
        "event_name": event_name,
        "payload": payload
    }
    logger.info(json.dumps(log_entry))
