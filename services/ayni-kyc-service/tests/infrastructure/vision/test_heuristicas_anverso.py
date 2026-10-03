"""Pruebas de las heuristicas de fallback: sin FastAPI, sin red, sin disco."""
from src.domain.model.resultado_verificacion import FuenteDatosIdentidad
from src.infrastructure.vision.heuristicas_anverso import (
    extraer_fecha_emision,
    extraer_por_heuristicas,
    extraer_segundo_apellido,
)


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


def test_debe_leer_los_rotulos_reales_del_dni_sin_devolver_el_rotulo_como_valor() -> None:
    # Dado: los rotulos tal como estan impresos en el DNI ("Primer Apellido",
    # "Pre Nombres"). Antes se devolvia "PRIMER" como apellido y "PRE" como nombre.
    texto_ocr = [
        "REPUBLICA DEL PERU",
        "DNI 44556677",
        "Primer Apellido",
        "QUISPE",
        "Segundo Apellido",
        "MAMANI",
        "Pre Nombres",
        "ANA LUCIA",
        "Fecha de Nacimiento",
        "15 05 1990",
        "Sexo",
        "F",
        "Fecha de Emisión",
        "20 08 2021",
    ]

    # Cuando
    resultado = extraer_por_heuristicas(texto_ocr)

    # Entonces
    assert resultado is not None
    assert resultado.dni == "44556677"
    assert resultado.apellidos == "QUISPE MAMANI"
    assert resultado.nombres == "ANA LUCIA"
    assert resultado.sexo == "F"
    assert resultado.fecha_nacimiento.isoformat() == "1990-05-15"
    assert resultado.fecha_emision is not None
    assert resultado.fecha_emision.isoformat() == "2021-08-20"


def test_debe_aceptar_prenombres_en_una_sola_palabra_como_en_el_dnie() -> None:
    texto_ocr = [
        "44556677",
        "Primer Apellido: QUISPE",
        "Prenombres: ANA LUCIA",
        "Sexo: F",
        "Nacimiento: 15/05/1990",
    ]

    resultado = extraer_por_heuristicas(texto_ocr)

    assert resultado is not None
    assert resultado.apellidos == "QUISPE"
    assert resultado.nombres == "ANA LUCIA"


def test_no_debe_tomar_el_rotulo_siguiente_como_valor_cuando_falta_el_dato() -> None:
    # Dado: el OCR perdio el valor del nombre; la linea siguiente es otro rotulo
    texto_ocr = [
        "44556677",
        "Primer Apellido",
        "QUISPE",
        "Pre Nombres",
        "Sexo",
        "F",
        "Fecha de Nacimiento",
        "15 05 1990",
    ]

    # Cuando / Entonces: sin nombre no hay extraccion, en vez de un nombre "SEXO"
    assert extraer_por_heuristicas(texto_ocr) is None


def test_debe_extraer_la_fecha_de_emision_aunque_no_haya_otros_datos() -> None:
    fecha = extraer_fecha_emision(["FECHA EMISION", "03-02-2020"])

    assert fecha is not None
    assert fecha.isoformat() == "2020-02-03"
    assert extraer_fecha_emision(["SEXO", "F"]) is None


def test_debe_encontrar_el_segundo_apellido_aunque_el_ocr_deforme_el_rotulo() -> None:
    # Texto tal como PaddleOCR lee un DNI azul real: rotulo deformado y "Fecha Emision"
    # colado entre el rotulo y el valor, porque estan a la misma altura.
    texto = ["LOA", "raner Apelido", "Segundo A pelido", "Fecha Emisión", "DENEGRI", "21082623"]

    assert extraer_segundo_apellido(texto) == "DENEGRI"


def test_no_debe_inventar_un_segundo_apellido_si_tras_el_rotulo_no_hay_letras() -> None:
    assert extraer_segundo_apellido(["Segundo Apellido", "Fecha Emision", "21 08 2023"]) is None
