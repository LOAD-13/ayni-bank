#!/usr/bin/env python3
"""Genera un mes simulado de la métrica `up` para medir la disponibilidad de HU-16 (AYNI-312).

Producción todavía no acumula un mes de métricas: hasta el despliegue de la retención de 35
días, Prometheus guardaba 15. Para demostrar la medición antes de tener ese mes, este script
escribe 30 días de `up` a 15 s para los cinco servicios, con caídas en los momentos que dice
un fichero de incidentes, en formato OpenMetrics. Después se cargan en un Prometheus real con
promtool y se mide con el mismo medir-disponibilidad.py que se usará con los datos reales:

    python infra/scripts/simular-mes-de-disponibilidad.py \\
        infra/observability/simulacion/incidentes-de-un-mes.json > mes.om
    promtool tsdb create-blocks-from openmetrics mes.om datos/
    # Prometheus sobre datos/ y luego, con el instante final que escribe este script:
    python infra/scripts/medir-disponibilidad.py --prometheus http://localhost:9090 --hasta <fin>

La ventana termina ahora mismo, para que la medición de los últimos 30 días caiga encima. Al
final escribe en stderr la caída esperada por servicio: la medición tiene que coincidir.

Solo usa la biblioteca estándar.
"""

from __future__ import annotations

import json
import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

RASPADO = 15
DIAS = 30
LIMA = timezone(timedelta(hours=-5))
ESENCIALES = {"ayni-gateway", "ayni-identity-service", "ayni-core-banking-service"}


def tramos(incidentes: list[dict], dia_cero: datetime) -> list[tuple[int, int, set[str], bool]]:
    """Cada incidente como (inicio, fin, servicios caídos, sin muestras) en segundos Unix."""
    resultado = []
    for i in incidentes:
        hora, minuto = (int(x) for x in i["hora"].split(":"))
        inicio = dia_cero + timedelta(days=i["dia"], hours=hora, minutes=minuto)
        desde = int(inicio.timestamp())
        resultado.append((desde, desde + i["segundos"], set(i["servicios"]), i.get("sin_muestras", False)))
    return resultado


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    config = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    sys.stderr.reconfigure(encoding="utf-8")

    fin = int(time.time()) // RASPADO * RASPADO
    inicio = fin - DIAS * 86400
    # Las horas de los incidentes son de Lima, contadas desde la medianoche del primer día.
    dia_cero = datetime.fromtimestamp(inicio, LIMA).replace(hour=0, minute=0, second=0)
    caidas = tramos(config["incidentes"], dia_cero)

    print("# HELP up Simulado para AYNI-312: 1 si el servicio respondió al raspado.")
    print("# TYPE up gauge")
    perdidas = {s: 0 for s in config["servicios"]}
    perdidas_banco = 0
    for servicio, instancia in config["servicios"].items():
        etiquetas = f'instance="{instancia}",job="ayni-servicios",servicio="{servicio}"'
        for t in range(inicio + RASPADO, fin + 1, RASPADO):
            activos = [c for c in caidas if c[0] <= t < c[1]]
            if any(sin_muestras for *_, sin_muestras in activos):
                perdidas[servicio] += 1
                continue  # Prometheus caído: no hay muestra de nadie
            caido = any(servicio in servicios for _, _, servicios, _ in activos)
            perdidas[servicio] += caido
            print(f"up{{{etiquetas}}} {0 if caido else 1} {t}")
    for t in range(inicio + RASPADO, fin + 1, RASPADO):
        activos = [c for c in caidas if c[0] <= t < c[1]]
        if any(c[3] or c[2] & ESENCIALES for c in activos):
            perdidas_banco += 1
    print("# EOF")

    esperadas = DIAS * 86400 // RASPADO
    print(f"Ventana: {datetime.fromtimestamp(inicio, timezone.utc):%d-%m-%Y %H:%M} → "
          f"{datetime.fromtimestamp(fin, timezone.utc):%d-%m-%Y %H:%M} UTC", file=sys.stderr)
    print(f"Banco (esenciales a la vez): caída esperada {perdidas_banco * RASPADO / 60:.1f} min, "
          f"disponibilidad esperada {1 - perdidas_banco / esperadas:.3%}", file=sys.stderr)
    for servicio, n in perdidas.items():
        print(f"  {servicio}: {n * RASPADO / 60:.1f} min", file=sys.stderr)
    hasta = datetime.fromtimestamp(fin, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    print(f"Medir con: medir-disponibilidad.py --hasta {hasta}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
