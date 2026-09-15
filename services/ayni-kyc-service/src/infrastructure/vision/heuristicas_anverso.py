"""Extraccion de datos de identidad por heuristicas de texto libre (fallback).

Se usa solo cuando el MRZ del reverso no se pudo leer o sus checksums no
validaron (ver mrz_td1.py y ADR-0015). Busca en el texto crudo de OCR del
anverso las etiquetas conocidas del DNI peruano y el texto que las sigue.

Sin checksum ni forma de validar la lectura: por eso el resultado siempre
se marca `confiable=False` (ver DatosIdentidadExtraidos).
"""
import re
from datetime import date

from src.domain.model.resultado_verificacion import DatosIdentidadExtraidos, FuenteDatosIdentidad

_PATRON_DNI = re.compile(r"\b\d{8}\b")
_PATRON_FECHA = re.compile(r"\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})\b")

_ETIQUETAS_APELLIDOS = ("APELLIDOS", "APELLIDO PATERNO", "APELLIDO")
_ETIQUETAS_NOMBRES = ("PRENOMBRES", "NOMBRES", "NOMBRE")
_ETIQUETAS_FECHA_NACIMIENTO = ("FECHA DE NACIMIENTO", "NACIMIENTO", "F. NACIMIENTO")
_ETIQUETAS_SEXO = ("SEXO",)


def extraer_por_heuristicas(lineas_texto: list[str]) -> DatosIdentidadExtraidos | None:
    """Retorna None si no se pudieron identificar los 5 campos requeridos."""
    lineas = [linea.strip().upper() for linea in lineas_texto if linea.strip()]

    dni = _buscar_dni(lineas)
    apellidos = _buscar_valor_tras_etiqueta(lineas, _ETIQUETAS_APELLIDOS)
    nombres = _buscar_valor_tras_etiqueta(lineas, _ETIQUETAS_NOMBRES)
    sexo = _buscar_sexo(lineas)
    fecha_nacimiento_texto = _buscar_valor_tras_etiqueta(lineas, _ETIQUETAS_FECHA_NACIMIENTO)
    fecha_nacimiento = _parsear_fecha_dd_mm_yyyy(fecha_nacimiento_texto) if fecha_nacimiento_texto else None
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
    )


def _buscar_dni(lineas: list[str]) -> str | None:
    for linea in lineas:
        coincidencia = _PATRON_DNI.search(linea)
        if coincidencia:
            return coincidencia.group()
    return None


def _buscar_valor_tras_etiqueta(lineas: list[str], etiquetas: tuple[str, ...]) -> str | None:
    for indice, linea in enumerate(lineas):
        for etiqueta in etiquetas:
            if etiqueta in linea:
                resto_misma_linea = linea.replace(etiqueta, "").strip(" :-")
                if resto_misma_linea:
                    return resto_misma_linea
                if indice + 1 < len(lineas):
                    return lineas[indice + 1].strip()
    return None


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
