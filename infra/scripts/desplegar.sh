#!/usr/bin/env bash
# Despliega una version de Ayni Bank en la instancia de produccion.
#
#   desplegar.sh <etiqueta-de-imagen> [version]
#
# Lo ejecuta el pipeline de despliegue (.github/workflows/cd.yml) en la
# instancia EC2 por AWS Systems Manager. No hay SSH ni claves que custodiar.
#
# Pasos:
#   1. Descarga del repositorio, en el commit exacto que se despliega, el
#      compose de produccion y el Caddyfile. El pipeline es la fuente de verdad.
#   2. Genera /opt/ayni/.env con los secretos de Parameter Store y Secrets
#      Manager. El fichero vive solo en la instancia, con permisos 600.
#   3. Asegura que existe el usuario de aplicacion en RDS (idempotente).
#   4. Descarga las imagenes y recrea solo los contenedores que cambian.
#   5. Espera a que todos los servicios esten sanos. Si en 6 minutos no lo
#      estan, vuelve a la version anterior y termina con error: produccion
#      nunca se queda con una version a medio arrancar.
set -euo pipefail

ETIQUETA="${1:?Falta la etiqueta de imagen}"
VERSION="${2:-$ETIQUETA}"
REPO="${AYNI_REPO:-LOAD-13/ayni-bank}"
COMMIT="${AYNI_COMMIT:-$ETIQUETA}"
DIR=/opt/ayni
REGION=us-east-1
CUENTA=$(aws sts get-caller-identity --query Account --output text)
REGISTRO="${CUENTA}.dkr.ecr.${REGION}.amazonaws.com"

mkdir -p "$DIR"
cd "$DIR"

registrar() { echo "[$(date -u +%FT%TZ)] $*"; }

# ── 1. Ficheros de despliegue del commit exacto ──────────────────────────
registrar "Descargando configuracion del commit ${COMMIT}"
for f in infra/docker/docker-compose.prod.yml infra/docker/Caddyfile; do
  curl -fsSL "https://raw.githubusercontent.com/${REPO}/${COMMIT}/${f}" -o "$(basename "$f").nuevo"
done
# Prometheus y Grafana (AYNI-159): origen en el repositorio -> destino en ./observabilidad
rm -rf observabilidad.nuevo
while read -r origen destino; do
  mkdir -p "observabilidad.nuevo/$(dirname "$destino")"
  curl -fsSL "https://raw.githubusercontent.com/${REPO}/${COMMIT}/infra/observability/${origen}"     -o "observabilidad.nuevo/${destino}"
done <<'LISTA'
prometheus.prod.yml prometheus.yml
grafana/datasources-prod/datasources.yml grafana/datasources/datasources.yml
grafana/dashboards/proveedor.yml grafana/dashboards/proveedor.yml
grafana/dashboards/ayni-tecnico.json grafana/dashboards/ayni-tecnico.json
grafana/dashboards/ayni-negocio.json grafana/dashboards/ayni-negocio.json
LISTA

# ── 2. Secretos ──────────────────────────────────────────────────────────
param() { aws ssm get-parameter --region "$REGION" --with-decryption --name "/ayni/prod/$1" --query Parameter.Value --output text; }
secreto() { aws secretsmanager get-secret-value --region "$REGION" --secret-id "$1" --query SecretString --output text; }

DB_HOST=$(param db-host)
ADMIN=$(secreto "$(param db-secreto-admin)")
ALMACEN=$(secreto ayni/prod/almacen)
IP=$(curl -fsS -H "X-aws-ec2-metadata-token: $(curl -fsS -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60')" http://169.254.169.254/latest/meta-data/public-ipv4)

umask 077
cat > .env.nuevo <<EOF
REGISTRO=${REGISTRO}
ETIQUETA=${ETIQUETA}
AYNI_VERSION=${VERSION}
AYNI_DOMINIO=ayni.${IP//./-}.sslip.io
AYNI_CORREO_TLS=$(param correo-tls)
DB_HOST=${DB_HOST}
DB_USUARIO=ayni_app
DB_CONTRASENA=$(param db-app-contrasena)
RABBITMQ_PASSWORD=$(param rabbitmq-contrasena)
AYNI_JWT_CLAVE=$(param jwt-clave)
# HU-07 (ADR-0031): firma del token de confirmacion con segundo factor. Obligatoria:
# sin ella identity y core-banking no arrancan.
AYNI_CONFIRMACION_CLAVE=$(param confirmacion-clave)
AYNI_CIFRADO_CLAVE=$(param cifrado-clave)
GRAFANA_CONTRASENA=$(param grafana-contrasena)
AYNI_BUCKET_KYC=$(param bucket-kyc)
AYNI_OPERADOR_CLAVE=$(param operador-clave 2>/dev/null || true)
S3_ACCESS_KEY=$(jq -r .access_key <<<"$ALMACEN")
S3_SECRET_KEY=$(jq -r .secret_key <<<"$ALMACEN")
EOF

# ── 3. Usuario de aplicacion en RDS ──────────────────────────────────────
# Los servicios no usan la cuenta maestra: usan ayni_app, duena de la base de
# la aplicacion pero sin privilegios sobre el resto de la instancia. Crear el
# rol es idempotente; la contrasena se reafirma en cada despliegue.
registrar "Verificando el usuario de aplicacion en la base de datos"
PGPASSWORD=$(jq -r .password <<<"$ADMIN") psql "host=${DB_HOST} dbname=ayni user=$(jq -r .username <<<"$ADMIN") sslmode=require" \
  -v ON_ERROR_STOP=1 -q -v clave="$(param db-app-contrasena)" <<'SQL'
SELECT 'CREATE ROLE ayni_app LOGIN' WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ayni_app')\gexec
ALTER ROLE ayni_app WITH LOGIN PASSWORD :'clave';
GRANT CONNECT, CREATE ON DATABASE ayni TO ayni_app;
SQL

# ── 4. Descargar y arrancar ──────────────────────────────────────────────
ANTERIOR=$(cat version-actual 2>/dev/null || true)
# Se guarda la configuracion que funciona para poder volver a ella.
for f in .env docker-compose.prod.yml Caddyfile; do
  [[ -f "$f" ]] && cp "$f" "$f.anterior"
done
mv .env.nuevo .env
mv docker-compose.prod.yml.nuevo docker-compose.prod.yml
mv Caddyfile.nuevo Caddyfile
rm -rf observabilidad.anterior
[[ -d observabilidad ]] && mv observabilidad observabilidad.anterior
mv observabilidad.nuevo observabilidad
# Grafana corre con el usuario 472 y Prometheus con nobody: los ficheros
# montados deben poder leerse aunque el umask del script sea 077.
chmod -R a+rX observabilidad

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRO" >/dev/null
# Antes de descargar, fuera las imagenes que no usa ningun contenedor: las de la version
# en marcha se conservan (son las del rollback). Sin esto el disco de 30 GB se lleno con
# versiones antiguas y el agente de SSM murio a mitad del despliegue de la 1.1.0.
docker image prune -af >/dev/null || true
registrar "Descargando imagenes ${ETIQUETA}"
docker compose --env-file .env -f docker-compose.prod.yml pull --quiet
registrar "Arrancando la version ${VERSION} (${ETIQUETA})"
docker compose --env-file .env -f docker-compose.prod.yml up -d --remove-orphans
# Caddy, Prometheus y Grafana leen ficheros montados desde /opt/ayni. El script los
# sustituye con mv, que crea un fichero nuevo: el contenedor en marcha sigue viendo el
# anterior y `up -d` no lo recrea si el compose no cambio. Sin este reinicio, un cambio
# en el Caddyfile no llega a produccion (asi lo detectaron las pruebas de humo en 1.1.0).
reiniciar_configurados() {
  docker compose --env-file .env -f docker-compose.prod.yml restart caddy ayni-prometheus ayni-grafana
}
reiniciar_configurados

# ── 5. Verificar salud o revertir ────────────────────────────────────────
# Sano = todos los contenedores del compose en marcha, ninguno arrancando ni
# enfermo. Se cuentan del propio compose para no olvidar actualizar un numero.
ESPERADOS=$(docker compose --env-file .env -f docker-compose.prod.yml config --services | wc -l)
esperar_salud() {
  local limite=$((SECONDS + 360)) estados enmarcha pendientes
  while (( SECONDS < limite )); do
    estados=$(docker ps --filter "name=ayni-" --format '{{.Status}}')
    enmarcha=$(grep -c '^Up' <<<"$estados" || true)
    pendientes=$(grep -cE 'unhealthy|starting|Restarting' <<<"$estados" || true)
    if (( enmarcha >= ESPERADOS && pendientes == 0 )); then
      return 0
    fi
    sleep 10
  done
  return 1
}

if esperar_salud; then
  echo "$ETIQUETA" > version-actual
  echo "$VERSION" > version-publicada
  docker image prune -af --filter "until=168h" >/dev/null || true
  registrar "OK: version ${VERSION} en produccion"
  docker ps --format 'table {{.Names}}\t{{.Status}}'
  exit 0
fi

registrar "ERROR: la version ${VERSION} no quedo sana"
docker ps --format 'table {{.Names}}\t{{.Status}}'
if [[ -n "$ANTERIOR" && -f .env.anterior ]]; then
  registrar "Revirtiendo a ${ANTERIOR}"
  for f in .env docker-compose.prod.yml Caddyfile; do
    [[ -f "$f.anterior" ]] && mv "$f.anterior" "$f"
  done
  if [[ -d observabilidad.anterior ]]; then
    rm -rf observabilidad && mv observabilidad.anterior observabilidad
  fi
  docker compose --env-file .env -f docker-compose.prod.yml up -d --remove-orphans
  reiniciar_configurados
  registrar "Revertido: sigue en produccion ${ANTERIOR}"
fi
exit 1
