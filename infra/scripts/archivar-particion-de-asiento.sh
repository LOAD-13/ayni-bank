#!/usr/bin/env bash
# Archiva en frio una particion mensual de core.asiento y la desvincula de la tabla.
#
#   AYNI_ARCHIVADO_HABILITADO=si archivar-particion-de-asiento.sh <AAAA_MM> <destino>
#
#   <destino>  s3://bucket/prefijo   o un directorio local (pruebas).
#   Conexion:  variables estandar de psql (PGHOST, PGUSER, PGDATABASE, PGPASSWORD...).
#
# DESACTIVADO A PROPOSITO (ADR-0036). El saldo de una cuenta es la suma de TODOS sus
# asientos. Desvincular una particion la saca de esa suma, y los saldos de quien tuvo
# movimientos ese mes dejarian de cuadrar. Antes de habilitarlo hace falta un asiento de
# saldo de apertura por cuenta al inicio de cada mes retenido, que sustituya a los
# asientos que salen. Hasta entonces el script se niega a correr sin la variable.
#
# Pasos (no borra ni un asiento: HU-15 exige pista de auditoria inalterable):
#   1. Comprueba que la particion existe y es mas antigua que la retencion (3 meses).
#   2. La vuelca con COPY a CSV comprimido y anota filas y SHA-256.
#   3. Sube el archivo y verifica que llego entero.
#   4. Solo entonces DETACH PARTITION. La tabla desvinculada se conserva en la base.
set -euo pipefail

MES="${1:?Falta el mes, formato AAAA_MM}"
DESTINO="${2:?Falta el destino: s3://... o un directorio}"
RETENCION_MESES=3

registrar() { echo "[$(date -u +%FT%TZ)] $*"; }

if [[ "${AYNI_ARCHIVADO_HABILITADO:-no}" != "si" ]]; then
  echo "Archivado desactivado: sin asiento de saldo de apertura, desvincular una particion" >&2
  echo "descuadra los saldos. Ver ADR-0036 antes de poner AYNI_ARCHIVADO_HABILITADO=si." >&2
  exit 2
fi
[[ "$MES" =~ ^[0-9]{4}_(0[1-9]|1[0-2])$ ]] || { echo "Mes invalido: $MES (AAAA_MM)" >&2; exit 1; }

PARTICION="asiento_${MES}"
sql() { psql -X -v ON_ERROR_STOP=1 -At "$@"; }

# ── 1. Existe, es una particion de asiento y ya salio de la retencion ─────
ES_PARTICION=$(sql -c "SELECT count(*) FROM pg_inherits WHERE inhparent = 'core.asiento'::regclass
                       AND inhrelid = to_regclass('core.${PARTICION}')")
[[ "$ES_PARTICION" == "1" ]] || { echo "core.${PARTICION} no es una particion de core.asiento" >&2; exit 1; }

VIEJA=$(sql -c "SELECT to_date('${MES}', 'YYYY_MM')
                 < date_trunc('month', now() AT TIME ZONE 'America/Lima') - make_interval(months => ${RETENCION_MESES})")
[[ "$VIEJA" == "t" ]] || { echo "${MES} sigue dentro de los ${RETENCION_MESES} meses de retencion" >&2; exit 1; }

# ── 2. Volcado comprimido con su huella ──────────────────────────────────
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
ARCHIVO="${PARTICION}.csv.gz"
FILAS=$(sql -c "SELECT count(*) FROM core.${PARTICION}")
registrar "Volcando core.${PARTICION} (${FILAS} asientos)"
sql -c "\copy (SELECT * FROM core.${PARTICION} ORDER BY registrado_en, id) TO STDOUT WITH (FORMAT csv, HEADER)" \
  | gzip -9 > "${TMP}/${ARCHIVO}"
VOLCADAS=$(( $(gzip -dc "${TMP}/${ARCHIVO}" | wc -l) - 1 ))
[[ "$VOLCADAS" == "$FILAS" ]] || { echo "El volcado tiene ${VOLCADAS} filas y la particion ${FILAS}" >&2; exit 1; }
(cd "$TMP" && sha256sum "$ARCHIVO" > "${ARCHIVO}.sha256")

# ── 3. Subida y verificacion ─────────────────────────────────────────────
registrar "Subiendo a ${DESTINO}"
if [[ "$DESTINO" == s3://* ]]; then
  aws s3 cp "${TMP}/${ARCHIVO}" "${DESTINO%/}/${ARCHIVO}" --only-show-errors
  aws s3 cp "${TMP}/${ARCHIVO}.sha256" "${DESTINO%/}/${ARCHIVO}.sha256" --only-show-errors
  aws s3 cp "${DESTINO%/}/${ARCHIVO}" "${TMP}/verificado.gz" --only-show-errors
else
  mkdir -p "$DESTINO"
  cp "${TMP}/${ARCHIVO}" "${TMP}/${ARCHIVO}.sha256" "$DESTINO/"
  cp "${DESTINO}/${ARCHIVO}" "${TMP}/verificado.gz"
fi
[[ "$(sha256sum < "${TMP}/verificado.gz")" == "$(sha256sum < "${TMP}/${ARCHIVO}")" ]] \
  || { echo "El archivo subido no coincide con el volcado: no se desvincula" >&2; exit 1; }

# ── 4. Desvincular (instantaneo) ─────────────────────────────────────────
sql -c "ALTER TABLE core.asiento DETACH PARTITION core.${PARTICION}" > /dev/null
registrar "core.${PARTICION} archivada (${FILAS} asientos) y desvinculada; la tabla se conserva"
