"""Verificacion del DNI (HU-02): evaluacion de cada foto y extraccion de datos.

Ambas operaciones son sincronas (diseno-base.md §3.5 punto 5): identity-service
espera la respuesta con un timeout de 10 s. La imagen no viaja por la API;
identity envia la clave del objeto y este servicio lo lee de MinIO.

Aqui solo se componen las piezas que ya existian (procesamiento_documento,
DetectorDocumentoOpenCV, ValidadorCalidadOpenCV, ExtractorDatosPaddleOCR). Cada
foto se descarga y se endereza una sola vez por peticion.
"""
from datetime import UTC, date, datetime
from functools import lru_cache
from typing import Annotated, Literal

from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from src.domain.model.resultado_verificacion import (
    FuenteDatosIdentidad,
    MotivoRechazoCaptura,
    ResultadoEvaluacionCaptura,
)
from src.domain.port.verificador_identidad import AlmacenObjetosPort, ExtractorDatosPort, ObjetoNoEncontradoError
from src.infrastructure.auditoria import log_audit_event
from src.infrastructure.config import settings
from src.infrastructure.storage.almacen_objetos_minio import AlmacenObjetosMinIO
from src.infrastructure.vision.detector_documento_opencv import DetectorDocumentoOpenCV
from src.infrastructure.vision.procesamiento_documento import decodificar_imagen, procesar_documento
from src.infrastructure.vision.validador_calidad_opencv import ValidadorCalidadOpenCV

router = APIRouter(prefix="/kyc/documentos", tags=["Documentos"])

TAMANO_MAXIMO_BYTES = 5 * 1024 * 1024

# Forma exacta de las claves que firma GenerarUrlDeSubidaService en identity:
# kyc/{solicitudId}/{lado}-{uuid}.{extension}. Rechazar cualquier otra cosa evita
# que se use este servicio para leer objetos ajenos al flujo de KYC.
_UUID = r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
PATRON_CLAVE_DE_DNI = rf"^kyc/{_UUID}/(anverso|reverso)-{_UUID}\.(jpg|jpeg|png|webp)$"


class _ModeloCamel(BaseModel):
    """El contrato OpenAPI usa camelCase; el codigo Python, snake_case."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, frozen=True)


class SolicitudEvaluacion(_ModeloCamel):
    document_key: str = Field(pattern=PATRON_CLAVE_DE_DNI)
    lado: Literal["ANVERSO", "REVERSO"]


class RespuestaEvaluacion(_ModeloCamel):
    aceptada: bool
    es_dni: bool
    nitida: bool
    sin_reflejos: bool
    bien_encuadrada: bool
    bien_iluminada: bool
    motivo_rechazo: MotivoRechazoCaptura | None


class SolicitudExtraccion(_ModeloCamel):
    anverso_document_key: str = Field(pattern=PATRON_CLAVE_DE_DNI)
    reverso_document_key: str = Field(pattern=PATRON_CLAVE_DE_DNI)


class DatosExtraidos(_ModeloCamel):
    dni: str
    nombres: str
    apellidos: str
    fecha_nacimiento: date
    sexo: str
    fecha_emision: date | None
    fuente: FuenteDatosIdentidad
    confiable: bool


class RespuestaExtraccion(_ModeloCamel):
    """`legible=False` no es un error: el OCR no encontro los datos y el titular debe repetir."""

    legible: bool
    datos: DatosExtraidos | None = None


@lru_cache
def obtener_almacen() -> AlmacenObjetosPort:
    return AlmacenObjetosMinIO(
        settings.MINIO_ENDPOINT, settings.MINIO_ACCESS_KEY, settings.MINIO_SECRET_KEY, settings.MINIO_BUCKET_KYC
    )


@lru_cache
def obtener_extractor() -> ExtractorDatosPort:
    # Importacion diferida: paddleocr tarda en importarse y carga el modelo al
    # construirse. Solo se paga cuando hace falta extraer (o al arrancar, si
    # KYC_PRECARGAR_OCR esta activo).
    from src.infrastructure.vision.extractor_datos_paddleocr import ExtractorDatosPaddleOCR

    return ExtractorDatosPaddleOCR(obtener_almacen())


def _error(estado: int, codigo: str, mensaje: str) -> JSONResponse:
    """ErrorResponse del contrato: {code, message, timestamp}."""
    return JSONResponse(
        status_code=estado,
        content={"code": codigo, "message": mensaje, "timestamp": datetime.now(UTC).isoformat()},
    )


def _no_encontrado() -> JSONResponse:
    return _error(404, "NOT_FOUND", "El documento indicado no existe en el almacen.")


def _evaluar(imagen_bytes: bytes, almacen: AlmacenObjetosPort) -> ResultadoEvaluacionCaptura:
    # Detector y validador no necesitan el almacen para evaluar una imagen ya
    # descargada; se les pasa el mismo para respetar su constructor.
    validador = ValidadorCalidadOpenCV(almacen)

    procesado = procesar_documento(imagen_bytes)
    if procesado is not None:
        if not DetectorDocumentoOpenCV(almacen).coincide_con_dni(procesado):
            return ResultadoEvaluacionCaptura(es_dni=False, calidad=None)
        return ResultadoEvaluacionCaptura(es_dni=True, calidad=validador.evaluar(procesado))

    imagen = decodificar_imagen(imagen_bytes)
    if imagen is None:
        return ResultadoEvaluacionCaptura(es_dni=False, calidad=None)
    return ResultadoEvaluacionCaptura(es_dni=False, calidad=validador.evaluar_sin_documento(imagen))


@router.post("/evaluacion", response_model=RespuestaEvaluacion)
def evaluar_captura(
    solicitud: SolicitudEvaluacion,
    almacen: Annotated[AlmacenObjetosPort, Depends(obtener_almacen)],
) -> RespuestaEvaluacion | JSONResponse:
    try:
        imagen_bytes = almacen.descargar(solicitud.document_key)
    except ObjetoNoEncontradoError:
        return _no_encontrado()

    if len(imagen_bytes) > TAMANO_MAXIMO_BYTES:
        return _error(400, "BAD_REQUEST", "La imagen excede el limite de 5 MB.")

    resultado = _evaluar(imagen_bytes, almacen)

    motivo = resultado.motivo_rechazo
    log_audit_event(
        "KYC_CAPTURA_DNI_EVALUADA",
        {"lado": solicitud.lado, "aceptada": resultado.aceptada, "motivo": motivo.value if motivo else None},
    )

    calidad = resultado.calidad
    return RespuestaEvaluacion(
        aceptada=resultado.aceptada,
        es_dni=resultado.es_dni,
        nitida=calidad is not None and calidad.es_nitida,
        sin_reflejos=calidad is not None and calidad.sin_reflejos,
        bien_encuadrada=calidad is not None and calidad.bien_encuadrada,
        bien_iluminada=calidad is not None and calidad.bien_iluminada,
        motivo_rechazo=motivo,
    )


@router.post("/extraccion", response_model=RespuestaExtraccion, response_model_exclude_none=True)
def extraer_datos(
    solicitud: SolicitudExtraccion,
    extractor: Annotated[ExtractorDatosPort, Depends(obtener_extractor)],
) -> RespuestaExtraccion | JSONResponse:
    try:
        datos = extractor.extraer(solicitud.anverso_document_key, solicitud.reverso_document_key)
    except ObjetoNoEncontradoError:
        return _no_encontrado()

    # Sin datos personales en la auditoria: solo de donde salieron y si son fiables.
    log_audit_event(
        "KYC_DATOS_DNI_EXTRAIDOS",
        {
            "legible": datos is not None,
            "fuente": datos.fuente.value if datos else None,
            "confiable": datos.confiable if datos else None,
        },
    )

    if datos is None:
        return RespuestaExtraccion(legible=False)

    return RespuestaExtraccion(
        legible=True,
        datos=DatosExtraidos(
            dni=datos.dni,
            nombres=datos.nombres,
            apellidos=datos.apellidos,
            fecha_nacimiento=datos.fecha_nacimiento,
            sexo=datos.sexo,
            fecha_emision=datos.fecha_emision,
            fuente=datos.fuente,
            confiable=datos.confiable,
        ),
    )
