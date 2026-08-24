"""Punto de entrada del servicio de verificacion de identidad."""
from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

app = FastAPI(
    title="Ayni KYC Service",
    description="Verificacion de identidad: deteccion de DNI, OCR, vivacidad y cotejo facial",
    version="0.1.0",
)

Instrumentator().instrument(app).expose(app, endpoint="/metrics")


@app.get("/health", tags=["operacion"])
def health() -> dict[str, str]:
    """Health check consumido por Docker Compose y por el orquestador."""
    return {"status": "UP", "service": "ayni-kyc-service"}
