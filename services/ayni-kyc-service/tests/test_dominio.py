"""Pruebas del dominio: sin FastAPI, sin red, sin disco."""
import pytest

from src.domain.model.resultado_verificacion import (
    DecisionVerificacion,
    ResultadoCotejoFacial,
)


def test_debe_crear_resultado_cuando_la_similitud_es_valida() -> None:
    # Dado / Cuando
    resultado = ResultadoCotejoFacial(
        similitud=0.87, supero_vivacidad=True, decision=DecisionVerificacion.APROBADA
    )

    # Entonces
    assert resultado.similitud == 0.87
    assert resultado.decision is DecisionVerificacion.APROBADA


def test_debe_rechazar_cuando_la_similitud_esta_fuera_de_rango() -> None:
    # Dado / Cuando / Entonces
    with pytest.raises(ValueError, match="entre 0.0 y 1.0"):
        ResultadoCotejoFacial(
            similitud=1.5, supero_vivacidad=True, decision=DecisionVerificacion.APROBADA
        )
