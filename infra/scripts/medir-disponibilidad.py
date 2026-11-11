#!/usr/bin/env python3
"""Mide la disponibilidad real de Ayni Bank y la contrasta con el 99.5 % de HU-16 (AYNI-312).

En la instancia de producción, donde Prometheus solo escucha en la red interna de Docker:

    python3 medir-disponibilidad.py --contenedor ayni-prometheus > disponibilidad.md

Contra un Prometheus accesible por HTTP (el local de docker-compose, o producción con un
reenvío de puerto de Systems Manager):

    python3 infra/scripts/medir-disponibilidad.py --prometheus http://localhost:9090

Qué cuenta como «disponible»
----------------------------
El banco está disponible cuando se puede operar con el dinero: gateway, identity y
core-banking responden **a la vez**. notification y kyc no son esenciales (HU-16: su caída
no impide operar), así que se informan aparte y no restan a la cifra del SLA.

Por qué no basta `avg_over_time(up[30d])`
-----------------------------------------
Ese promedio solo ve las muestras que existen. Si se cae la instancia entera, o el propio
Prometheus, no hay muestras de esos minutos y el promedio no se entera: daría 100 % justo en
el peor apagón. Aquí se divide entre las muestras que **debería** haber (ventana ÷ intervalo de
raspado) y cada muestra que falta cuenta como caída.

Solo usa la biblioteca estándar: corre tal cual en Amazon Linux.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import urllib.parse
import urllib.request
from datetime import datetime, timezone

OBJETIVO = 0.995
ESENCIALES = ["ayni-gateway", "ayni-identity-service", "ayni-core-banking-service"]
NO_ESENCIALES = ["ayni-notification-service", "ayni-kyc-service"]
UNIDADES = {"s": 1, "m": 60, "h": 3600, "d": 86400}


def segundos(duracion: str) -> int:
    """«30d» → 2 592 000. Solo una unidad: es lo que se escribe a mano en la consola."""
    return int(duracion[:-1]) * UNIDADES[duracion[-1]]


class Prometheus:
    def __init__(self, url: str | None, contenedor: str | None) -> None:
        self.url = url
        self.contenedor = contenedor

    def consultar(self, promql: str) -> list[dict]:
        ruta = "/api/v1/query?" + urllib.parse.urlencode({"query": promql})
        if self.contenedor:
            # La imagen de Prometheus trae el wget de busybox; no hace falta publicar el puerto.
            salida = subprocess.run(
                ["docker", "exec", self.contenedor, "wget", "-qO-", f"http://localhost:9090{ruta}"],
                check=True,
                capture_output=True,
                text=True,
            ).stdout
        else:
            with urllib.request.urlopen(f"{self.url}{ruta}", timeout=60) as respuesta:
                salida = respuesta.read().decode()
        cuerpo = json.loads(salida)
        if cuerpo.get("status") != "success":
            raise RuntimeError(f"Prometheus rechazó la consulta: {cuerpo}")
        return cuerpo["data"]["result"]

    def escalar(self, promql: str) -> float | None:
        resultado = self.consultar(promql)
        return float(resultado[0]["value"][1]) if resultado else None


def selector(servicios: list[str]) -> str:
    return 'servicio=~"' + "|".join(servicios) + '"'


def medir(prom: Prometheus, ventana: str, raspado: str) -> dict:
    esperadas = segundos(ventana) / segundos(raspado)

    # Todo el banco a la vez: el mínimo de `up` entre los esenciales, muestra a muestra.
    banco = prom.escalar(
        f"sum_over_time((min(up{{{selector(ESENCIALES)}}}))[{ventana}:{raspado}])"
    )
    por_servicio = {}
    for servicio in ESENCIALES + NO_ESENCIALES:
        arriba = prom.escalar(f'sum_over_time(up{{servicio="{servicio}"}}[{ventana}])')
        por_servicio[servicio] = (arriba or 0.0) / esperadas

    # Desde cuándo hay datos: si la retención es menor que la ventana, la medición no cubre
    # el periodo pedido y hay que decirlo en lugar de dar la cifra por buena.
    primera = prom.escalar(
        f"min(min_over_time(timestamp(up{{{selector(ESENCIALES)}}})[{ventana}:{raspado}]))"
    )
    return {
        "ventana": ventana,
        "raspado": raspado,
        "esperadas": esperadas,
        "banco": (banco or 0.0) / esperadas,
        "por_servicio": por_servicio,
        "primera_muestra": primera,
    }


def minutos(fraccion_caida: float, ventana: str) -> float:
    return fraccion_caida * segundos(ventana) / 60


def legible(segs: float) -> str:
    """Una duración en la unidad que se lee bien: «12.5 días», «3.2 h», «45 min»."""
    if segs >= 86400:
        return f"{segs / 86400:.1f} días"
    if segs >= 3600:
        return f"{segs / 3600:.1f} h"
    return f"{segs / 60:.0f} min"


def informe(m: dict) -> str:
    ahora = datetime.now(timezone.utc).strftime("%d-%m-%Y %H:%M UTC")
    presupuesto = minutos(1 - OBJETIVO, m["ventana"])
    caida = minutos(1 - m["banco"], m["ventana"])
    cumple = m["banco"] >= OBJETIVO
    lineas = [
        f"# Disponibilidad medida · ventana de {m['ventana']}",
        "",
        f"Medido el {ahora} con `infra/scripts/medir-disponibilidad.py` (AYNI-312).",
        "",
        "| Indicador | Valor |",
        "|---|---|",
        f"| Disponibilidad del banco (gateway + identity + core-banking a la vez) | **{m['banco']:.3%}** |",
        f"| Objetivo de HU-16 | {OBJETIVO:.1%} |",
        f"| Caída acumulada | {caida:.1f} min |",
        f"| Presupuesto de caída del objetivo | {presupuesto:.1f} min |",
        f"| Resultado | {'CUMPLE' if cumple else 'NO CUMPLE'} |",
        "",
        "| Servicio | Esencial | Disponibilidad | Caída (min) |",
        "|---|---|---|---|",
    ]
    for servicio, valor in m["por_servicio"].items():
        esencial = "sí" if servicio in ESENCIALES else "no"
        lineas.append(
            f"| {servicio} | {esencial} | {valor:.3%} | {minutos(1 - valor, m['ventana']):.1f} |"
        )

    if m["primera_muestra"] is not None:
        desde = datetime.fromtimestamp(m["primera_muestra"], timezone.utc)
        cubierto = (datetime.now(timezone.utc) - desde).total_seconds()
        lineas += ["", f"Primera muestra dentro de la ventana: {desde:%d-%m-%Y %H:%M} UTC."]
        # Los pasos de la subconsulta se alinean al intervalo de raspado: se tolera ese desfase.
        if cubierto < segundos(m["ventana"]) - 2 * segundos(m["raspado"]):
            lineas.append(
                f"**Atención:** Prometheus solo tiene datos de {legible(cubierto)} de los "
                f"{legible(segundos(m['ventana']))} pedidos. El tiempo sin datos cuenta como "
                "caída, así que la cifra es un límite inferior, no la disponibilidad real."
            )
    lineas += [
        "",
        "Cada muestra de raspado que falta (instancia o Prometheus caídos) cuenta como caída:",
        "la cifra no puede salir mejor de lo que fue.",
    ]
    return "\n".join(lineas)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    origen = parser.add_mutually_exclusive_group(required=True)
    origen.add_argument("--prometheus", help="URL de Prometheus, p. ej. http://localhost:9090")
    origen.add_argument("--contenedor", help="Contenedor de Prometheus en la instancia")
    parser.add_argument("--ventana", default="30d", help="Periodo a medir (por omisión 30d)")
    parser.add_argument("--raspado", default="15s", help="scrape_interval de Prometheus")
    args = parser.parse_args()
    # En Windows la salida redirigida a un .md saldría en cp1252 y rompería las tildes.
    sys.stdout.reconfigure(encoding="utf-8")

    m = medir(Prometheus(args.prometheus, args.contenedor), args.ventana, args.raspado)
    print(informe(m))
    # Código de salida útil para automatizarlo: 0 si cumple el SLA, 1 si no.
    return 0 if m["banco"] >= OBJETIVO else 1


if __name__ == "__main__":
    sys.exit(main())
