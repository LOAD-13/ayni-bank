"""Pruebas del parser de MRZ TD1: sin FastAPI, sin red, sin disco.

Los MRZ de prueba se construyen calculando sus propios checksums con la
funcion interna _checksum, en vez de copiar valores calculados a mano: un
error de calculo manual invalidaria el test silenciosamente.
"""
from src.infrastructure.vision.mrz_td1 import _checksum, encontrar_lineas_mrz, parsear_mrz


def _construir_mrz_valido() -> tuple[str, str, str]:
    numero_doc = "87654321<"
    chk_doc = _checksum(numero_doc)
    linea1 = f"I<PER{numero_doc}{chk_doc}" + "<" * 15

    fecha_nac, fecha_cad, sexo, nacionalidad = "950322", "300322", "M", "PER"
    chk_nac = _checksum(fecha_nac)
    chk_cad = _checksum(fecha_cad)
    opcional2 = "<" * 11
    campo_compuesto = linea1[5:30] + fecha_nac + str(chk_nac) + fecha_cad + str(chk_cad) + opcional2
    chk_compuesto = _checksum(campo_compuesto)
    linea2 = f"{fecha_nac}{chk_nac}{sexo}{fecha_cad}{chk_cad}{nacionalidad}{opcional2}{chk_compuesto}"

    nombre_apellido = "GARCIA<LOPEZ<<MARIA<JOSE"
    linea3 = nombre_apellido + "<" * (30 - len(nombre_apellido))

    return linea1, linea2, linea3


def test_debe_extraer_todos_los_campos_cuando_el_mrz_es_valido() -> None:
    # Dado
    linea1, linea2, linea3 = _construir_mrz_valido()

    # Cuando
    resultado = parsear_mrz(linea1, linea2, linea3)

    # Entonces
    assert resultado is not None
    assert resultado.numero_documento == "87654321"
    assert resultado.fecha_nacimiento.isoformat() == "1995-03-22"
    assert resultado.sexo == "M"
    assert resultado.fecha_caducidad.isoformat() == "2030-03-22"
    assert resultado.nacionalidad == "PER"
    assert resultado.apellidos == "GARCIA LOPEZ"
    assert resultado.nombres == "MARIA JOSE"
    assert resultado.todos_los_checksums_validos is True


def test_debe_marcar_checksum_invalido_cuando_el_ocr_leyo_mal_un_digito() -> None:
    # Dado: el OCR confundio un caracter del numero de documento (8 -> 6)
    linea1, linea2, linea3 = _construir_mrz_valido()
    linea1_con_error = linea1.replace("87654321", "87654361")

    # Cuando
    resultado = parsear_mrz(linea1_con_error, linea2, linea3)

    # Entonces: los datos se extraen igual, pero se marca como no confiable
    assert resultado is not None
    assert resultado.numero_documento == "87654361"
    assert resultado.todos_los_checksums_validos is False


def test_debe_retornar_none_cuando_alguna_linea_no_mide_30_caracteres() -> None:
    # Dado
    linea1, linea2, _ = _construir_mrz_valido()

    # Cuando / Entonces
    assert parsear_mrz(linea1, linea2, "MUY CORTA") is None


def test_debe_encontrar_las_tres_lineas_del_mrz_entre_texto_con_ruido() -> None:
    # Dado: texto crudo de OCR con lineas irrelevantes antes y despues del MRZ
    linea1, linea2, linea3 = _construir_mrz_valido()
    texto_ocr = ["REPUBLICA DEL PERU", "RENIEC", linea1, linea2, linea3, "FIRMA"]

    # Cuando
    encontradas = encontrar_lineas_mrz(texto_ocr)

    # Entonces
    assert encontradas == (linea1, linea2, linea3)


def test_debe_retornar_none_cuando_no_hay_tres_lineas_con_forma_de_mrz() -> None:
    # Dado
    texto_ocr = ["REPUBLICA DEL PERU", "RENIEC", "FIRMA"]

    # Cuando / Entonces
    assert encontrar_lineas_mrz(texto_ocr) is None
