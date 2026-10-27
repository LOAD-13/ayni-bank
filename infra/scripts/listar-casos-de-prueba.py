#!/usr/bin/env python3
"""Genera el listado de casos de prueba del repositorio (AYNI-219, plan de pruebas).

    python infra/scripts/listar-casos-de-prueba.py > docs/calidad/casos-de-prueba.md

Lee el código de las pruebas, no un inventario a mano que se desactualiza: JUnit 5 (con su
@DisplayName), reglas de ArchUnit, escenarios Gherkin, pytest, Vitest y Playwright. Cada caso
recibe un identificador estable dentro de su componente (CP-<componente>-NNN) por orden de
archivo y de aparición. Solo usa la biblioteca estándar.
"""

from __future__ import annotations

import re
import subprocess
import sys
from collections import Counter
from dataclasses import dataclass
from datetime import date
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]

COMPONENTES = {
    "ayni-identity-service": "IDN",
    "ayni-core-banking-service": "CORE",
    "ayni-notification-service": "NOT",
    "ayni-gateway": "GW",
    "ayni-kyc-service": "KYC",
    "ayni-web": "WEB",
}

NIVELES = ["Unitaria", "Arquitectura", "Integración", "Aceptación (Gherkin)", "Navegador (E2E)", "Usabilidad"]


@dataclass(frozen=True)
class Caso:
    componente: str
    archivo: str
    nombre: str
    nivel: str
    nota: str = ""


def relativo(ruta: Path) -> str:
    return ruta.relative_to(RAIZ).as_posix()


# Pruebas que levantan contenedores o un contexto de Spring: varias piezas reales juntas.
INTEGRACION_JAVA = re.compile(r"@(Testcontainers|SpringBootTest|WebMvcTest|WebFluxTest|DataJpaTest|JdbcTest)\b")


def nivel_java(ruta: Path) -> str:
    texto = ruta.as_posix().lower()
    if "aceptacion" in texto:
        return "Aceptación (Gherkin)"
    if "integracion" in texto or INTEGRACION_JAVA.search(ruta.read_text(encoding="utf-8")):
        return "Integración"
    if "arquitectura" in texto:
        return "Arquitectura"
    return "Unitaria"


ANOTACION_TEST = re.compile(r"^\s*@(Test|ParameterizedTest|RepeatedTest)\b")
DISPLAY = re.compile(r'@DisplayName\("((?:[^"\\]|\\.)*)"\)')
METODO = re.compile(r"\bvoid\s+(\w+)\s*\(")
ARCH = re.compile(r"@ArchTest\s+(?:static\s+)?(?:final\s+)?ArchRule\s+(\w+)")


def casos_java(componente: str, ruta: Path) -> list[Caso]:
    lineas = ruta.read_text(encoding="utf-8").splitlines()
    nivel = nivel_java(ruta)
    casos: list[Caso] = []
    pendiente = False
    nombre: str | None = None
    for linea in lineas:
        if ANOTACION_TEST.search(linea):
            pendiente, nombre = True, None
            continue
        if pendiente:
            if m := DISPLAY.search(linea):
                nombre = m.group(1).replace('\\"', '"')
            elif m := METODO.search(linea):
                casos.append(Caso(componente, relativo(ruta), nombre or humanizar(m.group(1)), nivel))
                pendiente = False
    texto = "\n".join(lineas)
    for m in ARCH.finditer(texto):
        casos.append(Caso(componente, relativo(ruta), humanizar(m.group(1)), "Arquitectura"))
    return casos


ESCENARIO = re.compile(r"^\s*(Escenario|Esquema del escenario|Scenario Outline|Scenario):\s*(.+)$")


def casos_gherkin(componente: str, ruta: Path) -> list[Caso]:
    casos: list[Caso] = []
    etiquetas = ""
    for linea in ruta.read_text(encoding="utf-8").splitlines():
        if linea.strip().startswith("@"):
            etiquetas = linea
            continue
        if m := ESCENARIO.match(linea):
            nota = "pendiente: describe una brecha, no se ejecuta" if "@pendiente" in etiquetas else ""
            casos.append(Caso(componente, relativo(ruta), m.group(2).strip(), "Aceptación (Gherkin)", nota))
        if linea.strip():
            etiquetas = ""
    return casos


PYTEST = re.compile(r"^\s*(?:async\s+)?def\s+(test_\w+)\s*\(")


def casos_pytest(componente: str, ruta: Path) -> list[Caso]:
    # La capa web se prueba con el TestClient de FastAPI, la aplicacion entera; el resto, por piezas.
    texto = ruta.read_text(encoding="utf-8")
    nivel = "Integración" if "TestClient" in texto else "Unitaria"
    return [
        Caso(componente, relativo(ruta), humanizar(m.group(1).removeprefix("test_")), nivel)
        for linea in texto.splitlines()
        if (m := PYTEST.match(linea))
    ]


JS_TEST = re.compile(r"""^\s*(?:it|test)(?:\.(?:only|skip|fixme))?\(\s*(["'`])((?:(?!\1).)*)\1""")


def casos_js(componente: str, ruta: Path, nivel: str) -> list[Caso]:
    casos = []
    for linea in ruta.read_text(encoding="utf-8").splitlines():
        if m := JS_TEST.match(linea):
            nombre = m.group(2)
            nota = "se repite por cada valor del bucle" if "${" in nombre else ""
            casos.append(Caso(componente, relativo(ruta), nombre, nivel, nota))
    return casos


def humanizar(identificador: str) -> str:
    con_espacios = re.sub(r"(?<=[a-záéíóúñ0-9])(?=[A-Z])", " ", identificador).replace("_", " ")
    return con_espacios[:1].upper() + con_espacios[1:].lower()


def recolectar() -> list[Caso]:
    casos: list[Caso] = []
    for servicio in sorted((RAIZ / "services").iterdir()):
        componente = servicio.name
        if componente not in COMPONENTES:
            continue
        for ruta in sorted(servicio.glob("src/test/**/*.java")):
            casos += casos_java(componente, ruta)
        for ruta in sorted(servicio.glob("src/test/**/*.feature")):
            casos += casos_gherkin(componente, ruta)
        for ruta in sorted(servicio.glob("tests/**/test_*.py")):
            casos += casos_pytest(componente, ruta)
    web = RAIZ / "web" / "ayni-web"
    for ruta in sorted(web.glob("src/**/*.test.ts*")):
        casos += casos_js("ayni-web", ruta, "Unitaria")
    for ruta in sorted(web.glob("e2e/**/*.spec.ts")):
        nivel = "Usabilidad" if "usabilidad" in ruta.as_posix() else "Navegador (E2E)"
        casos += casos_js("ayni-web", ruta, nivel)
    return casos


def celda(texto: str) -> str:
    return texto.replace("|", "\\|")


def commit_actual() -> str:
    try:
        salida = subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=RAIZ, capture_output=True, text=True, check=True)
        return salida.stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "desconocido"


def markdown(casos: list[Caso]) -> str:
    por_nivel = Counter((c.componente, c.nivel) for c in casos)
    salida = [
        "# Listado de casos de prueba",
        "",
        f"> Generado con `infra/scripts/listar-casos-de-prueba.py` el {date.today():%d-%m-%Y} sobre el commit "
        f"`{commit_actual()}`. **No se edita a mano**: se vuelve a generar. Anexo del "
        "[plan de pruebas](plan-de-pruebas.md).",
        "",
        "## Resumen por componente y nivel",
        "",
        "| Componente | " + " | ".join(NIVELES) + " | Total |",
        "|---|" + "---:|" * (len(NIVELES) + 1),
    ]
    for componente in COMPONENTES:
        fila = [por_nivel[(componente, n)] for n in NIVELES]
        salida.append(f"| `{componente}` | " + " | ".join(str(x) for x in fila) + f" | **{sum(fila)}** |")
    totales = [sum(por_nivel[(c, n)] for c in COMPONENTES) for n in NIVELES]
    salida.append("| **Total** | " + " | ".join(f"**{x}**" for x in totales) + f" | **{sum(totales)}** |")
    pendientes = sum(1 for c in casos if c.nota.startswith("pendiente"))
    salida += [
        "",
        f"Los escenarios Gherkin marcados como pendientes ({pendientes}) documentan un criterio que el código "
        "todavía no cumple. Se cuentan como casos especificados, pero no se ejecutan.",
    ]
    for componente, prefijo in COMPONENTES.items():
        propios = [c for c in casos if c.componente == componente]
        salida += ["", f"## `{componente}`", "", "| ID | Nivel | Caso | Archivo |", "|---|---|---|---|"]
        for i, caso in enumerate(propios, start=1):
            nombre = celda(caso.nombre) + (f" *({caso.nota})*" if caso.nota else "")
            archivo = caso.archivo.rsplit("/", 1)[-1]
            salida.append(f"| CP-{prefijo}-{i:03d} | {caso.nivel} | {nombre} | `{archivo}` |")
    return "\n".join(salida) + "\n"


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    print(markdown(recolectar()), end="")
