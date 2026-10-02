"""Pruebas de las heuristicas de fallback: sin FastAPI, sin red, sin disco."""
from src.domain.model.resultado_verificacion import FuenteDatosIdentidad
from src.infrastructure.vision.heuristicas_anverso import extraer_por_heuristicas


def test_debe_extraer_los_campos_cuando_las_etiquetas_estan_en_lineas_separadas() -> None:
    # Dado: texto crudo tipico de OCR, cada etiqueta en su propia linea
    texto_ocr = [
        "REPUBLICA DEL PERU",
        "DNI",
        "87654321",
        "APELLIDOS",
        "GARCIA LOPEZ",
        "NOMBRES",
        "MARIA JOSE",
        "SEXO",
        "F",
        "FECHA DE NACIMIENTO",
        "22/03/1995",
    ]

    # Cuando
    resultado = extraer_por_heuristicas(texto_ocr)

    # Entonces
    assert resultado is not None
    assert resultado.dni == "87654321"
    assert resultado.apellidos == "GARCIA LOPEZ"
    assert resultado.nombres == "MARIA JOSE"
    assert resultado.sexo == "F"
    assert resultado.fecha_nacimiento.isoformat() == "1995-03-22"
    assert resultado.fuente is FuenteDatosIdentidad.HEURISTICA_ANVERSO
    assert resultado.confiable is False


def test_debe_extraer_el_valor_cuando_esta_en_la_misma_linea_que_la_etiqueta() -> None:
    # Dado
    texto_ocr = [
        "APELLIDOS: GARCIA LOPEZ",
        "NOMBRES: MARIA JOSE",
        "SEXO: F",
        "12345678",
        "15/01/1988",
    ]

    # Cuando
    resultado = extraer_por_heuristicas(texto_ocr)

    # Entonces
    assert resultado is not None
    assert resultado.apellidos == "GARCIA LOPEZ"
    assert resultado.nombres == "MARIA JOSE"
    assert resultado.sexo == "F"


def test_debe_retornar_none_cuando_falta_un_campo_requerido() -> None:
    # Dado: sin fecha de nacimiento en ningun lado
    texto_ocr = ["APELLIDOS", "GARCIA LOPEZ", "NOMBRES", "MARIA JOSE", "SEXO", "F", "12345678"]

    # Cuando / Entonces
    assert extraer_por_heuristicas(texto_ocr) is None


def test_debe_retornar_none_cuando_el_texto_esta_vacio() -> None:
    assert extraer_por_heuristicas([]) is None
