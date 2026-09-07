"""Parser de la zona de lectura mecanica (MRZ) del DNI peruano, formato TD1.

El DNI peruano (version electronica) trae en el reverso una MRZ de 3 lineas
de 30 caracteres, formato ICAO 9303 parte 5 (el mismo que usan las tarjetas
de identidad de muchos paises, y con la misma estructura que un pasaporte).
Es preferible al layout visual del anverso porque:
  1. Las posiciones de cada campo son fijas y estandar, no hay que adivinar
     donde esta cada dato segun el diseno grafico del documento.
  2. Trae digitos verificadores (checksum) que permiten CONFIRMAR que el OCR
     leyo bien, en vez de asumirlo (ver ADR-0009 sobre lecturas plausibles
     pero erroneas).
  3. Usa la fuente OCR-B, diseñada para ser leida por maquinas: mucho mas
     facil de reconocer para cualquier motor de OCR que texto normal.

Ver ADR-0015 para la decision de usar MRZ con fallback a heuristicas.
"""
import re
from dataclasses import dataclass
from datetime import date

_PESOS = (7, 3, 1)


def _valor_caracter(caracter: str) -> int:
    if caracter.isdigit():
        return int(caracter)
    if caracter == "<":
        return 0
    if "A" <= caracter <= "Z":
        return ord(caracter) - ord("A") + 10
    raise ValueError(f"Caracter invalido en MRZ: {caracter!r}")


def _checksum(campo: str) -> int:
    total = sum(_valor_caracter(c) * _PESOS[i % 3] for i, c in enumerate(campo))
    return total % 10


@dataclass(frozen=True)
class DatosMrz:
    """Datos extraidos y validados de la MRZ (formato TD1)."""

    numero_documento: str
    fecha_nacimiento: date
    sexo: str
    fecha_caducidad: date
    nacionalidad: str
    apellidos: str
    nombres: str
    todos_los_checksums_validos: bool


def _fecha_desde_yymmdd(yymmdd: str, es_fecha_nacimiento: bool) -> date | None:
    """Convierte YYMMDD a date. Asume siglo 19xx/20xx segun el tipo de fecha."""
    if not yymmdd.isdigit() or len(yymmdd) != 6:
        return None

    yy, mm, dd = int(yymmdd[0:2]), int(yymmdd[2:4]), int(yymmdd[4:6])
    # Las fechas de caducidad de un DNI vigente siempre son 20xx. Para
    # nacimiento: nadie nace con una MRZ vigente y mas de ~100 anios, se
    # asume 19xx si yy sugiere una fecha futura respecto al presente.
    siglo_ambiguo = yy > date.today().year % 100
    siglo = 1900 if (es_fecha_nacimiento and siglo_ambiguo) else 2000

    try:
        return date(siglo + yy, mm, dd)
    except ValueError:
        return None


def parsear_mrz(linea1: str, linea2: str, linea3: str) -> DatosMrz | None:
    """Parsea 3 lineas de MRZ TD1 (30 caracteres cada una).

    Retorna None si las lineas no tienen el formato esperado (longitud
    incorrecta, fechas invalidas). El campo `todos_los_checksums_validos`
    indica si el OCR probablemente leyo bien: si es False, el llamador
    deberia desconfiar de estos datos y usar el fallback de heuristicas.
    """
    if len(linea1) != 30 or len(linea2) != 30 or len(linea3) != 30:
        return None

    numero_documento_crudo = linea1[5:14]
    checksum_documento = linea1[14]
    numero_documento = numero_documento_crudo.replace("<", "")

    fecha_nacimiento_cruda = linea2[0:6]
    checksum_nacimiento = linea2[6]
    sexo = linea2[7]
    fecha_caducidad_cruda = linea2[8:14]
    checksum_caducidad = linea2[14]
    nacionalidad = linea2[15:18]

    fecha_nacimiento = _fecha_desde_yymmdd(fecha_nacimiento_cruda, es_fecha_nacimiento=True)
    fecha_caducidad = _fecha_desde_yymmdd(fecha_caducidad_cruda, es_fecha_nacimiento=False)
    if fecha_nacimiento is None or fecha_caducidad is None:
        return None

    apellidos, _, nombres = linea3.rstrip("<").partition("<<")
    apellidos = apellidos.replace("<", " ").strip()
    nombres = nombres.replace("<", " ").strip()

    campo_compuesto = linea1[5:30] + linea2[0:7] + linea2[8:15] + linea2[18:29]

    checksums_validos = (
        checksum_documento.isdigit()
        and _checksum(numero_documento_crudo) == int(checksum_documento)
        and checksum_nacimiento.isdigit()
        and _checksum(fecha_nacimiento_cruda) == int(checksum_nacimiento)
        and checksum_caducidad.isdigit()
        and _checksum(fecha_caducidad_cruda) == int(checksum_caducidad)
        and linea2[29].isdigit()
        and _checksum(campo_compuesto) == int(linea2[29])
    )

    return DatosMrz(
        numero_documento=numero_documento,
        fecha_nacimiento=fecha_nacimiento,
        sexo=sexo,
        fecha_caducidad=fecha_caducidad,
        nacionalidad=nacionalidad,
        apellidos=apellidos,
        nombres=nombres,
        todos_los_checksums_validos=checksums_validos,
    )


_PATRON_LINEA_MRZ = re.compile(r"^[A-Z0-9<]{28,32}$")


def encontrar_lineas_mrz(lineas_texto: list[str]) -> tuple[str, str, str] | None:
    """Busca 3 lineas consecutivas con forma de MRZ dentro del texto crudo de OCR.

    El OCR no siempre da exactamente 30 caracteres por ruido/recorte, por eso
    se acepta un rango (28-32) y se normaliza con padding/recorte de '<' antes
    de parsear.
    """
    candidatas = [
        linea.strip().upper().replace(" ", "")
        for linea in lineas_texto
        if _PATRON_LINEA_MRZ.match(linea.strip().upper().replace(" ", ""))
    ]
    if len(candidatas) < 3:
        return None

    for i in range(len(candidatas) - 2):
        tres_lineas = candidatas[i : i + 3]
        normalizadas = tuple(_normalizar_longitud(linea) for linea in tres_lineas)
        if all(len(linea) == 30 for linea in normalizadas):
            return normalizadas  # type: ignore[return-value]

    return None


def _normalizar_longitud(linea: str) -> str:
    if len(linea) == 30:
        return linea
    if len(linea) > 30:
        return linea[:30]
    return linea + "<" * (30 - len(linea))
