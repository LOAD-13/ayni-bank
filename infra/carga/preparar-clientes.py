#!/usr/bin/env python3
"""Prepara clientes de prueba para las pruebas de carga y estrés en el entorno LOCAL (AYNI-300).

    python infra/carga/preparar-clientes.py --clientes 50 > tokens.txt
    k6 run -e BASE_URL=http://localhost:8080 -e TOKENS="$(cat tokens.txt)" infra/carga/estres.js

Abrir una cuenta de verdad exige el onboarding entero (DNI, selfie, segundo factor). Para
medir el núcleo transaccional no hace falta: el script publica en RabbitMQ el mismo evento
`identity.solicitud.aprobada` que identity emite al aprobar un KYC, de modo que core-banking
abre la cuenta y emite la tarjeta por su camino real. Después deposita en cada cuenta para que
el historial tenga movimientos, y escribe un token de acceso por cliente, separados por comas.

Los tokens se firman con la clave JWT de DESARROLLO del gateway (application.yml), así que
solo sirven contra el compose local. En staging o producción la clave es otra y el gateway
los rechaza: el script no puede usarse para entrar en un entorno real.

Solo usa la biblioteca estándar. Lee RABBITMQ_USER y RABBITMQ_PASSWORD del .env de la raíz.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
# Clave de desarrollo del gateway: valor por omisión de ayni.jwt.clave en su application.yml.
CLAVE_JWT_DESARROLLO = "YXluaS1maXJtYS1kZS1kZXNhcnJvbGxvLTMyYnl0ZXM="
PERMISOS_DE_CLIENTE = ["cuenta:leer", "transferencia:iniciar", "tarjeta:administrar"]


def leer_env() -> dict[str, str]:
    valores = {}
    for linea in (RAIZ / ".env").read_text(encoding="utf-8").splitlines():
        if "=" in linea and not linea.lstrip().startswith("#"):
            clave, valor = linea.split("=", 1)
            valores[clave.strip()] = valor.strip()
    return valores


def b64url(datos: bytes) -> str:
    return base64.urlsafe_b64encode(datos).rstrip(b"=").decode()


def token_para(usuario: str, horas: int) -> str:
    ahora = int(time.time())
    cabecera = {"alg": "HS256", "typ": "JWT"}
    cuerpo = {
        "iss": "ayni-identity-service",
        "sub": usuario,
        "iat": ahora,
        "exp": ahora + horas * 3600,
        "permisos": PERMISOS_DE_CLIENTE,
    }
    firmado = f"{b64url(json.dumps(cabecera).encode())}.{b64url(json.dumps(cuerpo).encode())}"
    firma = hmac.new(base64.b64decode(CLAVE_JWT_DESARROLLO), firmado.encode(), hashlib.sha256)
    return f"{firmado}.{b64url(firma.digest())}"


def pedir(metodo: str, url: str, cuerpo: dict | None = None, cabeceras: dict | None = None):
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    peticion = urllib.request.Request(url, data=datos, method=metodo, headers=cabeceras or {})
    if datos is not None:
        peticion.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(peticion, timeout=30) as respuesta:
        texto = respuesta.read().decode()
        return respuesta.status, (json.loads(texto) if texto else None)


def publicar_aprobacion(rabbit: str, credenciales: str, usuario: str) -> None:
    evento = {
        "solicitudId": str(uuid.uuid4()),
        "usuarioId": usuario,
        "aprobadaEn": datetime.now(timezone.utc).isoformat(),
    }
    _, respuesta = pedir(
        "POST",
        f"{rabbit}/api/exchanges/%2F/ayni.eventos/publish",
        {
            "properties": {"content_type": "application/json", "delivery_mode": 2},
            "routing_key": "identity.solicitud.aprobada",
            "payload": json.dumps(evento),
            "payload_encoding": "string",
        },
        {"Authorization": f"Basic {credenciales}"},
    )
    if not respuesta or not respuesta.get("routed"):
        raise RuntimeError("RabbitMQ no enrutó el evento: ¿está arriba core-banking?")


def esperar_cuenta(gateway: str, token: str, intentos: int = 30) -> dict:
    for _ in range(intentos):
        try:
            _, cuenta = pedir("GET", f"{gateway}/api/v1/cuentas/mia", cabeceras={"Authorization": f"Bearer {token}"})
            return cuenta
        except urllib.error.HTTPError as error:
            if error.code != 404:
                raise
            time.sleep(1)
    raise RuntimeError("La cuenta no se abrió a tiempo")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--clientes", type=int, default=50)
    parser.add_argument("--depositos", type=int, default=3, help="Depósitos por cliente")
    parser.add_argument("--gateway", default="http://localhost:8080")
    parser.add_argument("--rabbitmq", default="http://localhost:15672")
    parser.add_argument("--horas", type=int, default=12, help="Vigencia de los tokens")
    args = parser.parse_args()

    env = leer_env()
    credenciales = base64.b64encode(f"{env['RABBITMQ_USER']}:{env['RABBITMQ_PASSWORD']}".encode()).decode()
    tokens = []
    for i in range(args.clientes):
        usuario = str(uuid.uuid4())
        token = token_para(usuario, args.horas)
        publicar_aprobacion(args.rabbitmq, credenciales, usuario)
        esperar_cuenta(args.gateway, token)
        for d in range(args.depositos):
            pedir(
                "POST",
                f"{args.gateway}/api/v1/cuentas/mia/depositos-simulados",
                {"importe": f"{100 + 50 * d}.00"},
                {"Authorization": f"Bearer {token}", "Idempotency-Key": str(uuid.uuid4())},
            )
        tokens.append(token)
        print(f"cliente {i + 1}/{args.clientes} listo", file=sys.stderr)

    print(",".join(tokens))
    return 0


if __name__ == "__main__":
    sys.exit(main())
