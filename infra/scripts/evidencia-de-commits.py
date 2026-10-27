#!/usr/bin/env python3
"""Genera la evidencia del historial de commits por autor y por rama (AYNI-224, Anexo H).

    python infra/scripts/evidencia-de-commits.py [rama] > docs/gestion/anexo-h-historial-de-commits.md

Por defecto lee `origin/develop`. Las ramas de trabajo se borran al fusionar (GitFlow del
proyecto), así que la distribución por rama sale de los merges de los pull requests: cada uno
dice qué rama entró y sus commits son los que traía. Los autores se agrupan por correo, porque
una misma persona firma a veces con su usuario de GitHub y a veces con su nombre. Los correos
no se publican. Solo usa la biblioteca estándar.
"""

from __future__ import annotations

import re
import subprocess
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
LIMA = timezone(timedelta(hours=-5))

# Correo de git -> nombre con el que figura en el equipo.
PERSONAS = {
    "jloadenegri@gmail.com": "Joaquín Loa Denegri",
    "kiarasantti@gmail.com": "Kiara Santti Saavedra",
    "u22234544@utp.edu.pe": "Eduardo Vargas Zumaeta",
    "knight.tekk3n.23@gmail.com": "Gerardo Raúl Socualaya Mandamiento",
}

CONVENCIONAL = re.compile(r"^(feat|fix|docs|test|build|ci|chore|refactor|perf|style|revert)(\([^)]+\))?!?: \S")
MERGE_PR = re.compile(r"^Merge pull request #(\d+) from [^/]+/(\S+)")
SEP = "\x1f"


@dataclass(frozen=True)
class Commit:
    hash: str
    padres: tuple[str, ...]
    autor: str
    fecha: datetime
    asunto: str
    cuerpo: str

    @property
    def es_merge(self) -> bool:
        return len(self.padres) > 1


def git(*args: str) -> str:
    return subprocess.run(["git", *args], cwd=RAIZ, capture_output=True, text=True, encoding="utf-8", check=True).stdout


def persona(nombre: str, correo: str) -> str:
    return PERSONAS.get(correo.lower(), nombre)


def leer(rama: str) -> list[Commit]:
    formato = SEP.join(["%H", "%P", "%an", "%ae", "%aI", "%s", "%b"]) + "\x1e"
    commits = []
    for registro in git("log", rama, f"--format={formato}").split("\x1e"):
        if not registro.strip():
            continue
        h, padres, nombre, correo, fecha, asunto, cuerpo = registro.strip("\n").split(SEP)
        commits.append(Commit(h, tuple(padres.split()), persona(nombre, correo),
                              datetime.fromisoformat(fecha).astimezone(LIMA), asunto, cuerpo))
    return commits


def ramas_fusionadas(commits: list[Commit]) -> list[tuple[int, str, list[str]]]:
    """(numero de PR, rama, hashes que traia) por cada merge de pull request."""
    salida = []
    for c in commits:
        if c.es_merge and (m := MERGE_PR.match(c.asunto)):
            traidos = git("rev-list", "--no-merges", f"{c.padres[0]}..{c.padres[1]}").split()
            salida.append((int(m.group(1)), m.group(2), traidos))
    return salida


def tipo_de_rama(rama: str) -> str:
    return rama.split("/", 1)[0] if "/" in rama else "otra"


def celda(texto: str) -> str:
    return texto.replace("|", "\\|")


def porcentaje(parte: int, total: int) -> str:
    return f"{parte / total * 100:.1f} %".replace(".", ",") if total else "—"


def markdown(rama: str) -> str:
    commits = leer(rama)
    propios = [c for c in commits if not c.es_merge]
    por_hash = {c.hash: c for c in propios}
    fusiones = ramas_fusionadas(commits)
    total = len(propios)
    desde = min(c.fecha for c in propios)
    hasta = max(c.fecha for c in propios)
    convencionales = sum(1 for c in propios if CONVENCIONAL.match(c.asunto))
    con_ticket = sum(1 for c in propios if re.search(r"^Refs: AYNI-\d+", c.cuerpo, re.M))
    cabeza = git("rev-parse", "--short", rama).strip()

    s = [
        "# Anexo H · Evidencia del historial de commits",
        "",
        f"> Generado con `infra/scripts/evidencia-de-commits.py` sobre `{rama}` en el commit `{cabeza}`. "
        "**No se edita a mano**: se vuelve a generar. Fechas y horas en hora de Lima (UTC−5).",
        "",
        "## 1. Resumen",
        "",
        "| | |",
        "|---|---|",
        f"| Periodo | {desde:%d-%m-%Y} → {hasta:%d-%m-%Y} |",
        f"| Commits (sin contar los de merge) | {total} |",
        f"| Pull requests fusionados | {len(fusiones)} |",
        f"| Commits de merge | {len(commits) - total} |",
        f"| Mensajes con Conventional Commits (`tipo(ámbito): …`) | {convencionales} de {total} ({porcentaje(convencionales, total)}) |",
        f"| Commits con referencia a Jira (`Refs: AYNI-NN`) | {con_ticket} de {total} ({porcentaje(con_ticket, total)}) |",
        "",
        "## 2. Por autor",
        "",
        "| Autor | Commits | % | Primer commit | Último commit | PR en los que participa |",
        "|---|---:|---:|---|---|---:|",
    ]
    por_autor: dict[str, list[Commit]] = defaultdict(list)
    for c in propios:
        por_autor[c.autor].append(c)
    prs_por_autor = Counter(a for _, _, traidos in fusiones
                            for a in {por_hash[h].autor for h in traidos if h in por_hash})
    for autor, lista in sorted(por_autor.items(), key=lambda kv: -len(kv[1])):
        s.append(f"| {autor} | {len(lista)} | {porcentaje(len(lista), total)} | "
                 f"{min(c.fecha for c in lista):%d-%m-%Y %H:%M} | {max(c.fecha for c in lista):%d-%m-%Y %H:%M} | "
                 f"{prs_por_autor[autor]} |")

    s += ["", "## 3. Por tipo de commit", "", "| Tipo | Commits | % |", "|---|---:|---:|"]
    tipos = Counter((m.group(1) if (m := CONVENCIONAL.match(c.asunto)) else "sin tipo") for c in propios)
    for tipo, n in tipos.most_common():
        s.append(f"| `{tipo}` | {n} | {porcentaje(n, total)} |")

    s += ["", "## 4. Por tipo de rama (GitFlow)", "",
          "| Tipo de rama | Ramas fusionadas | Commits que aportaron |", "|---|---:|---:|"]
    por_tipo: dict[str, list[int]] = defaultdict(list)
    for _, nombre, traidos in fusiones:
        por_tipo[tipo_de_rama(nombre)].append(len(traidos))
    for tipo, cuentas in sorted(por_tipo.items(), key=lambda kv: -len(kv[1])):
        s.append(f"| `{tipo}` | {len(cuentas)} | {sum(cuentas)} |")

    s += ["", "## 5. Por rama fusionada", "",
          "Cada fila es un pull request: la rama que entró en la rama de integración, cuántos commits traía y de quién.",
          "", "| PR | Rama | Commits | Autores | Fusionado |", "|---:|---|---:|---|---|"]
    fecha_merge = {int(m.group(1)): c.fecha for c in commits if c.es_merge and (m := MERGE_PR.match(c.asunto))}
    for numero, nombre, traidos in sorted(fusiones, key=lambda f: -f[0]):
        autores = ", ".join(sorted({por_hash[h].autor for h in traidos if h in por_hash})) or "—"
        s.append(f"| #{numero} | `{nombre}` | {len(traidos)} | {autores} | {fecha_merge[numero]:%d-%m-%Y %H:%M} |")

    s += ["", "## 6. Actividad por semana", "", "| Semana (lunes) | Commits | Autores |", "|---|---:|---|"]
    semanas: dict[str, list[Commit]] = defaultdict(list)
    for c in propios:
        lunes = (c.fecha - timedelta(days=c.fecha.weekday())).strftime("%d-%m-%Y")
        semanas[lunes].append(c)
    for lunes, lista in sorted(semanas.items(), key=lambda kv: datetime.strptime(kv[0], "%d-%m-%Y")):
        s.append(f"| {lunes} | {len(lista)} | {', '.join(sorted({c.autor for c in lista}))} |")

    s += ["", "## 7. Registro completo", "", "| Fecha y hora | Commit | Autor | Mensaje |", "|---|---|---|---|"]
    for c in propios:
        s.append(f"| {c.fecha:%d-%m-%Y %H:%M} | `{c.hash[:7]}` | {c.autor} | {celda(c.asunto)} |")
    return "\n".join(s) + "\n"


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    print(markdown(sys.argv[1] if len(sys.argv) > 1 else "origin/develop"), end="")
