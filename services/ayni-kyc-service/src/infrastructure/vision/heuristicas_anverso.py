"""Extraccion de datos de identidad por heuristicas de texto libre (fallback).

Se usa solo cuando el MRZ del reverso no se pudo leer o sus checksums no
validaron (ver mrz_td1.py y ADR-0015). Busca en el texto crudo de OCR del
anverso las etiquetas conocidas del DNI peruano y el texto que las sigue.

Sin checksum ni forma de validar la lectura: por eso el resultado siempre
se marca `confiable=False` (ver DatosIdentidadExtraidos).

Los rotulos del DNI real son "Primer Apellido", "Segundo Apellido" y
"Pre Nombres" (o "Prenombres" en el DNIe). Cada tupla de etiquetas va de la
mas larga a la mas corta: si "APELLIDO" se probara antes que "PRIMER APELLIDO",
el resto de la linea ("PRIMER") se devolveria como si fuera el apellido.
"""
import re
from datetime import date

from src.domain.model.resultado_verificacion import DatosIdentidadExtraidos, FuenteDatosIdentidad

_PATRON_DNI = re.compile(r"\b\d{8}\b")
# El DNI imprime las fechas separadas por espacios ("15 05 1990"); el OCR a veces
# devuelve barras, guiones o puntos en su lugar.
_PATRON_FECHA = re.compile(r"\b(\d{1,2})[/\-.\s](\d{1,2})[/\-.\s](\d{4})\b")

_ETIQUETAS_PRIMER_APELLIDO = ("PRIMER APELLIDO", "APELLIDO PATERNO")
_ETIQUETAS_SEGUNDO_APELLIDO = ("SEGUNDO APELLIDO", "APELLIDO MATERNO")
_ETIQUETAS_APELLIDOS = ("APELLIDOS",)
_ETIQUETAS_NOMBRES = ("PRE NOMBRES", "PRENOMBRES", "NOMBRES", "NOMBRE")
_ETIQUETAS_FECHA_NACIMIENTO = ("FECHA DE NACIMIENTO", "F. NACIMIENTO", "NACIMIENTO")
_ETIQUETAS_FECHA_EMISION = ("FECHA DE EMISION", "FECHA EMISION", "F. EMISION", "EMISION")
_ETIQUETAS_SEXO = ("SEXO",)

_TODAS_LAS_ETIQUETAS = (
    _ETIQUETAS_PRIMER_APELLIDO
    + _ETIQUETAS_SEGUNDO_APELLIDO
    + _ETIQUETAS_APELLIDOS
    + _ETIQUETAS_NOMBRES
    + _ETIQUETAS_FECHA_NACIMIENTO
    + _ETIQUETAS_FECHA_EMISION
    + _ETIQUETAS_SEXO
)


def extraer_por_heuristicas(lineas_texto: list[str]) -> DatosIdentidadExtraidos | None:
    """Retorna None si no se pudieron identificar los 5 campos requeridos."""
    lineas = _normalizar(lineas_texto)

    dni = _buscar_dni(lineas)
    apellidos = _buscar_apellidos(lineas)
    nombres = _buscar_valor_tras_etiqueta(lineas, _ETIQUETAS_NOMBRES)
    sexo = _buscar_sexo(lineas)
    fecha_nacimiento = _buscar_fecha_tras_etiqueta(lineas, _ETIQUETAS_FECHA_NACIMIENTO)
    if fecha_nacimiento is None:
        fecha_nacimiento = _buscar_primera_fecha(lineas)

    if dni is None or apellidos is None or nombres is None or sexo is None or fecha_nacimiento is None:
        return None

    return DatosIdentidadExtraidos(
        dni=dni,
        nombres=nombres,
        apellidos=apellidos,
        fecha_nacimiento=fecha_nacimiento,
        sexo=sexo,
        fuente=FuenteDatosIdentidad.HEURISTICA_ANVERSO,
        confiable=False,
        fecha_emision=_buscar_fecha_tras_etiqueta(lineas, _ETIQUETAS_FECHA_EMISION),
    )


def extraer_fecha_emision(lineas_texto: list[str]) -> date | None:
    """Fecha de emision leida del anverso.

    El MRZ del reverso no la trae (solo nacimiento y caducidad), asi que se lee
    del anverso tambien cuando el resto de datos vino del MRZ.
    """
    return _buscar_fecha_tras_etiqueta(_normalizar(lineas_texto), _ETIQUETAS_FECHA_EMISION)


_PATRON_SOLO_LETRAS = re.compile(r"^[A-ZÑ]+(?: [A-ZÑ]+)*$")
_PALABRAS_DE_ROTULO = ("APELLIDO", "PELIDO", "NOMBRE", "FECHA", "EMISION", "SEXO", "ESTADO", "DNI", "NACIMIENTO",
                       "CADUCIDAD", "INSCRIPCION", "UBIGEO", "REPUBLICA", "DOCUMENTO", "DUPLICADO", "REGIST")


def extraer_segundo_apellido(lineas_texto: list[str]) -> str | None:
    """El segundo apellido impreso en el anverso, para completar el MRZ que a veces no lo trae.

    El OCR deforma el rotulo ("Segundo A pelido"), asi que se reconoce por la palabra
    SEGUNDO y se toma la primera linea siguiente hecha solo de letras que no sea otro rotulo
    (entre ambas suele colarse "Fecha Emision", que esta a la misma altura).
    """
    lineas = _normalizar(lineas_texto)
    for indice, linea in enumerate(lineas):
        if "SEGUNDO" not in linea:
            continue
        for candidata in lineas[indice + 1 : indice + 4]:
            limpia = candidata.strip(" :-.")
            if _PATRON_SOLO_LETRAS.match(limpia) and not any(p in limpia for p in _PALABRAS_DE_ROTULO):
                return limpia
        return None
    return None


def _normalizar(lineas_texto: list[str]) -> list[str]:
    # Sin tildes para que "EMISIÓN" y "EMISION" se reconozcan igual: el OCR no
    # siempre las lee.
    tabla = str.maketrans("ÁÉÍÓÚ", "AEIOU")
    return [linea.strip().upper().translate(tabla) for linea in lineas_texto if linea.strip()]


def _buscar_dni(lineas: list[str]) -> str | None:
    for linea in lineas:
        coincidencia = _PATRON_DNI.search(linea)
        if coincidencia:
            return coincidencia.group()
    return None


def _buscar_apellidos(lineas: list[str]) -> str | None:
    primero = _buscar_valor_tras_etiqueta(lineas, _ETIQUETAS_PRIMER_APELLIDO)
    if primero is None:
        return _buscar_valor_tras_etiqueta(lineas, _ETIQUETAS_APELLIDOS)

    segundo = _buscar_valor_tras_etiqueta(lineas, _ETIQUETAS_SEGUNDO_APELLIDO)
    return f"{primero} {segundo}" if segundo else primero


def _buscar_valor_tras_etiqueta(lineas: list[str], etiquetas: tuple[str, ...]) -> str | None:
    for indice, linea in enumerate(lineas):
        for etiqueta in etiquetas:
            if etiqueta in linea:
                resto_misma_linea = linea.replace(etiqueta, "").strip(" :-")
                if resto_misma_linea:
                    return resto_misma_linea
                if indice + 1 < len(lineas) and not _es_etiqueta(lineas[indice + 1]):
                    return lineas[indice + 1].strip()
                break
    return None


def _es_etiqueta(linea: str) -> bool:
    """Una linea que solo contiene un rotulo no es el valor del rotulo anterior."""
    return any(linea.replace(etiqueta, "").strip(" :-") == "" for etiqueta in _TODAS_LAS_ETIQUETAS)


def _buscar_fecha_tras_etiqueta(lineas: list[str], etiquetas: tuple[str, ...]) -> date | None:
    texto = _buscar_valor_tras_etiqueta(lineas, etiquetas)
    return _parsear_fecha_dd_mm_yyyy(texto) if texto else None


def _buscar_sexo(lineas: list[str]) -> str | None:
    for indice, linea in enumerate(lineas):
        if any(etiqueta in linea for etiqueta in _ETIQUETAS_SEXO):
            candidatos = [linea]
            if indice + 1 < len(lineas):
                candidatos.append(lineas[indice + 1])
            for candidato in candidatos:
                if re.search(r"\bM\b", candidato):
                    return "M"
                if re.search(r"\bF\b", candidato):
                    return "F"
    return None


def _parsear_fecha_dd_mm_yyyy(texto: str) -> date | None:
    coincidencia = _PATRON_FECHA.search(texto)
    if not coincidencia:
        return None
    dd, mm, yyyy = (int(grupo) for grupo in coincidencia.groups())
    try:
        return date(yyyy, mm, dd)
    except ValueError:
        return None


def _buscar_primera_fecha(lineas: list[str]) -> date | None:
    for linea in lineas:
        fecha = _parsear_fecha_dd_mm_yyyy(linea)
        if fecha is not None:
            return fecha
    return None
