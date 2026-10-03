"""Punto de entrada del servicio de verificacion de identidad."""
import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from datetime import UTC, datetime

# PaddlePaddle (OCR, HU-02) tiene que cargarse ANTES que TensorFlow, que entra con
# DeepFace (cotejo facial, HU-03) al importar router_kyc. En el orden inverso, importar
# paddle en el mismo proceso provoca un segfault (codigo 139) sin traza: se comprobo en
# la imagen del servicio. Con este orden conviven. Ver ADR-0028.
import paddle  # noqa: F401
from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from prometheus_fastapi_instrumentator import Instrumentator

from src.infrastructure.config import settings
from src.infrastructure.web.router_documentos import obtener_extractor
from src.infrastructure.web.router_documentos import router as documentos_router
from src.infrastructure.web.router_kyc import router as kyc_router

logger = logging.getLogger(__name__)


@asynccontextmanager
async def ciclo_de_vida(_: FastAPI) -> AsyncIterator[None]:
    if settings.KYC_PRECARGAR_OCR:
        # Carga el modelo de OCR antes de aceptar trafico (ver config.py). Si falla,
        # el servicio arranca igual: evaluar fotos (HU-02) y cotejar rostros (HU-03)
        # no dependen del OCR, y tumbar todo por el OCR los dejaria sin servicio.
        # La extraccion lo reintentara en la primera peticion; si vuelve a fallar,
        # identity la trata como kyc-service no disponible (escenario 5).
        try:
            obtener_extractor()
        except Exception:
            obtener_extractor.cache_clear()
            logger.exception("No se pudo precargar el OCR; se reintentara en la primera extraccion.")
    yield


app = FastAPI(
    title="Ayni KYC Service",
    description="Verificacion de identidad: deteccion de DNI, OCR, vivacidad y cotejo facial",
    version="0.2.0",
    lifespan=ciclo_de_vida,
)

app.include_router(kyc_router)
app.include_router(documentos_router)

Instrumentator().instrument(app).expose(app, endpoint="/metrics")


@app.exception_handler(RequestValidationError)
async def al_fallar_la_validacion(_: Request, excepcion: RequestValidationError) -> JSONResponse:
    """Peticion mal formada: 400 con el ErrorResponse del contrato, no el 422 de FastAPI.

    Sin el detalle de pydantic en la respuesta: repetiria la entrada recibida, que
    puede incluir claves de objetos de otros solicitantes.
    """
    campos = sorted({".".join(str(parte) for parte in error["loc"][1:]) for error in excepcion.errors()})
    return JSONResponse(
        status_code=400,
        content={
            "code": "BAD_REQUEST",
            "message": "Solicitud invalida: " + ", ".join(campos),
            "timestamp": datetime.now(UTC).isoformat(),
        },
    )


@app.get("/health", tags=["operacion"])
def health() -> dict[str, str]:
    """Health check consumido por Docker Compose y por el orquestador."""
    return {"status": "UP", "service": "ayni-kyc-service"}
