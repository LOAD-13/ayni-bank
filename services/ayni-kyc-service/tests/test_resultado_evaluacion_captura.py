"""Orden de los motivos de rechazo de una captura (dominio puro, sin OpenCV)."""
import pytest

from src.domain.model.resultado_verificacion import (
    MotivoRechazoCaptura,
    ResultadoEvaluacionCaptura,
    ResultadoValidacionCalidad,
)


def _calidad(
    nitida: bool = True, sin_reflejos: bool = True, encuadrada: bool = True, iluminada: bool = True
) -> ResultadoValidacionCalidad:
    return ResultadoValidacionCalidad(
        es_nitida=nitida, sin_reflejos=sin_reflejos, bien_encuadrada=encuadrada, bien_iluminada=iluminada
    )


def test_una_captura_de_dni_sin_problemas_se_acepta() -> None:
    resultado = ResultadoEvaluacionCaptura(es_dni=True, calidad=_calidad())

    assert resultado.aceptada is True
    assert resultado.motivo_rechazo is None


def test_sin_nada_que_medir_el_motivo_es_que_no_es_un_dni() -> None:
    assert ResultadoEvaluacionCaptura(es_dni=False, calidad=None).motivo_rechazo is MotivoRechazoCaptura.NO_ES_DNI


@pytest.mark.parametrize(
    ("calidad", "motivo"),
    [
        (_calidad(nitida=False), MotivoRechazoCaptura.DESENFOQUE),
        (_calidad(iluminada=False), MotivoRechazoCaptura.ILUMINACION),
        (_calidad(encuadrada=False), MotivoRechazoCaptura.NO_ES_DNI),
    ],
)
def test_sin_documento_reconocido_se_culpa_primero_a_la_toma(
    calidad: ResultadoValidacionCalidad, motivo: MotivoRechazoCaptura
) -> None:
    assert ResultadoEvaluacionCaptura(es_dni=False, calidad=calidad).motivo_rechazo is motivo


@pytest.mark.parametrize(
    ("calidad", "motivo"),
    [
        (_calidad(encuadrada=False, nitida=False), MotivoRechazoCaptura.ENCUADRE),
        (_calidad(nitida=False, sin_reflejos=False), MotivoRechazoCaptura.DESENFOQUE),
        (_calidad(sin_reflejos=False, iluminada=False), MotivoRechazoCaptura.REFLEJO),
        (_calidad(iluminada=False), MotivoRechazoCaptura.ILUMINACION),
    ],
)
def test_con_varios_problemas_se_informa_el_que_conviene_corregir_primero(
    calidad: ResultadoValidacionCalidad, motivo: MotivoRechazoCaptura
) -> None:
    resultado = ResultadoEvaluacionCaptura(es_dni=True, calidad=calidad)

    assert resultado.aceptada is False
    assert resultado.motivo_rechazo is motivo


def test_un_anverso_enviado_como_reverso_se_rechaza_por_lado_incorrecto() -> None:
    resultado = ResultadoEvaluacionCaptura(es_dni=True, calidad=_calidad(), lado_incorrecto=True)

    assert resultado.aceptada is False
    assert resultado.motivo_rechazo is MotivoRechazoCaptura.LADO_INCORRECTO


def test_el_lado_incorrecto_se_informa_antes_que_la_calidad() -> None:
    resultado = ResultadoEvaluacionCaptura(es_dni=True, calidad=_calidad(nitida=False), lado_incorrecto=True)

    assert resultado.motivo_rechazo is MotivoRechazoCaptura.LADO_INCORRECTO
