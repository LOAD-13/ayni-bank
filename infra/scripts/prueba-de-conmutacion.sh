#!/usr/bin/env bash
# Prueba de conmutacion del laboratorio de alta disponibilidad (AYNI-154, ADR-0037).
#
#   infra/scripts/prueba-de-conmutacion.sh [confirmaciones]   (por defecto 500)
#
# Mide lo que promete el nivel 1 y lo que cuesta:
#   1. Coste de la confirmacion sincrona: N inserciones con remote_apply frente a N en local.
#   2. RPO: justo despues del ultimo COMMIT, la replica ya tiene las N filas.
#   3. Mata el primario (docker kill: sin apagado ordenado, como un corte de luz).
#   4. Promueve la replica y cronometra hasta que acepta escrituras: RTO de la base.
#   5. Verifica que no se perdio ninguna transaccion confirmada y que se puede escribir.
#
# En el laboratorio la promocion la dispara este script. En produccion la hace RDS
# Multi-AZ sola, y ademas mueve el DNS del endpoint: ese es el trabajo de Patroni o repmgr
# cuando la base no es gestionada. Al terminar, el laboratorio queda con la replica como
# primario: `docker compose -f infra/docker/ha/docker-compose.yml down -v` para repetir.
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash en Windows reescribiria las rutas de docker exec

N="${1:-500}"
COMPOSE=(docker compose -f "$(dirname "$0")/../docker/ha/docker-compose.yml")
PRIMARIO=ayni-ha-primario
REPLICA=ayni-ha-replica

sql() { docker exec -i "$1" psql -U ayni -d postgres -X -v ON_ERROR_STOP=1 -Atq "${@:2}"; }
ms() { date +%s%3N; }
registrar() { echo "[$(date +%T)] $*"; }
falla() { echo "FALLA: $*" >&2; exit 1; }

"${COMPOSE[@]}" up -d --wait >/dev/null
until [[ "$(sql "$PRIMARIO" -c "SELECT sync_state FROM pg_stat_replication WHERE application_name = 'replica'")" == "sync" ]]; do
  sleep 1
done
registrar "Replica conectada en modo sincrono"

sql "$PRIMARIO" -c "SET client_min_messages = warning; DROP TABLE IF EXISTS ha_prueba; CREATE TABLE ha_prueba (n INT PRIMARY KEY, en TIMESTAMPTZ DEFAULT clock_timestamp())"

# ── 1. Coste de la confirmacion sincrona ─────────────────────────────────
# Cada INSERT va en su propia transaccion (autocommit): N confirmaciones, como N operaciones.
inserciones() { for i in $(seq "$1" "$2"); do echo "INSERT INTO ha_prueba (n) VALUES ($i);"; done; }
t=$(ms); { echo "SET synchronous_commit = local;"; inserciones 1 "$N"; } | sql "$PRIMARIO"; LOCAL=$(( $(ms) - t ))
t=$(ms); inserciones $((N + 1)) $((2 * N)) | sql "$PRIMARIO"; SINCRONO=$(( $(ms) - t ))
TOTAL=$((2 * N))

# ── 2. RPO: lo confirmado ya esta en la replica ──────────────────────────
EN_REPLICA=$(sql "$REPLICA" -c "SELECT count(*) FROM ha_prueba")
[[ "$EN_REPLICA" == "$TOTAL" ]] || falla "la replica tiene ${EN_REPLICA} de ${TOTAL} filas confirmadas"
registrar "La replica tiene las ${TOTAL} filas en cuanto el primario confirma"

# ── 3 y 4. Caida del primario y promocion ────────────────────────────────
docker kill "$PRIMARIO" >/dev/null
CAIDA=$(ms)
registrar "Primario muerto (docker kill)"
sql "$REPLICA" -c "SELECT pg_promote(wait => true)" >/dev/null
until [[ "$(sql "$REPLICA" -c "SELECT pg_is_in_recovery()")" == "f" ]]; do sleep 0.1; done
RTO=$(( $(ms) - CAIDA ))
registrar "Replica promovida a primario"

# ── 5. Nada perdido y se puede escribir ──────────────────────────────────
TRAS=$(sql "$REPLICA" -c "SELECT count(*) FROM ha_prueba")
[[ "$TRAS" == "$TOTAL" ]] || falla "tras la conmutacion quedan ${TRAS} de ${TOTAL} filas"
sql "$REPLICA" -c "INSERT INTO ha_prueba (n) VALUES ($((TOTAL + 1)))"

cat <<EOF

  Resultado de la prueba de conmutacion
  ─────────────────────────────────────
  Confirmaciones locales      ${N} en ${LOCAL} ms   ($(awk "BEGIN {printf \"%.2f\", ${LOCAL}/${N}}") ms cada una)
  Confirmaciones sincronas    ${N} en ${SINCRONO} ms   ($(awk "BEGIN {printf \"%.2f\", ${SINCRONO}/${N}}") ms cada una)
  Transacciones perdidas      0 de ${TOTAL}   (RPO 0)
  Caida -> nuevo primario     ${RTO} ms   (RTO de la base, promocion manual)
EOF
