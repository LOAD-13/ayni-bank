#!/usr/bin/env bash
# Pruebas de humo contra la URL publica de produccion, justo despues de
# desplegar. Si una falla, el pipeline revierte a la version anterior.
#
#   pruebas-humo.sh <url-base> <version-esperada>
#
# No sustituyen a las pruebas del CI —esas ya pasaron—: comprueban que lo que
# se ve desde internet es la version nueva y que se comporta como debe.
set -uo pipefail

URL="${1:?Falta la URL base}"
VERSION="${2:?Falta la version esperada}"
FALLOS=0

ok()    { echo "  OK    $*"; }
falla() { echo "  FALLA $*"; FALLOS=$((FALLOS + 1)); }

codigo() { curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$@"; }

echo "Pruebas de humo contra ${URL} (version esperada ${VERSION})"

# La aplicacion puede tardar unos segundos en aceptar trafico tras arrancar.
for i in $(seq 1 30); do
  [[ "$(codigo "${URL}/api/health")" == "200" ]] && break
  sleep 10
done

# 1. Salud del backend (gateway y, tras el, los servicios)
SALUD=$(curl -sS --max-time 20 "${URL}/api/health" || true)
grep -q '"status":"UP"' <<<"$SALUD" && ok "GET /api/health -> UP" || falla "GET /api/health -> ${SALUD:0:120}"

# 2. La web responde
[[ "$(codigo "${URL}/")" == "200" ]] && ok "GET / -> 200" || falla "GET / no devuelve 200"

# 3. Es la version nueva y no la anterior
curl -sS --max-time 20 "${URL}/ingresar" | grep -q "v${VERSION}" \
  && ok "La pantalla de ingreso muestra v${VERSION}" \
  || falla "La pantalla de ingreso no muestra v${VERSION}"

# 4. HTTP redirige a HTTPS
HTTP="http://${URL#https://}"
[[ "$(codigo "${HTTP}/")" =~ ^30[178]$ ]] && ok "HTTP redirige a HTTPS" || falla "HTTP no redirige a HTTPS"

# 5. Cabeceras de seguridad
CABECERAS=$(curl -sSI --max-time 20 "${URL}/")
grep -qi '^strict-transport-security' <<<"$CABECERAS" && ok "Cabecera HSTS presente" || falla "Falta HSTS"
grep -qi '^x-frame-options: *deny' <<<"$CABECERAS" && ok "X-Frame-Options: DENY" || falla "Falta X-Frame-Options"
grep -qi '^x-powered-by' <<<"$CABECERAS" && falla "Se expone X-Powered-By" || ok "Sin X-Powered-By"

# 6. Un cuerpo invalido se rechaza con 400, no con 200 ni 500
[[ "$(codigo -X POST -H 'Content-Type: application/json' -d '{"correo":"no-es-correo"}' "${URL}/api/v1/sesion")" == "400" ]] \
  && ok "POST /api/v1/sesion invalido -> 400" || falla "POST /api/v1/sesion invalido no devuelve 400"

# 7. Una ruta protegida sin token se rechaza con 401
[[ "$(codigo "${URL}/api/v1/cuentas/mia")" == "401" ]] \
  && ok "GET /api/v1/cuentas/mia sin token -> 401" || falla "Ruta protegida accesible sin token"

echo
if (( FALLOS > 0 )); then
  echo "${FALLOS} prueba(s) de humo fallaron."
  exit 1
fi
echo "Todas las pruebas de humo pasaron."
