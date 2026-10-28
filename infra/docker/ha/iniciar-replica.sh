#!/bin/sh
# Arranque de la réplica del laboratorio de alta disponibilidad (ADR-0037).
#
# La primera vez clona el primario con pg_basebackup: -R deja escrita la conexión de
# vuelta al primario, con application_name=replica, que es el nombre que el primario
# espera en synchronous_standby_names; -C -S crea un slot de replicación para que el
# primario no recicle WAL que la réplica aún no ha recibido. Las siguientes veces
# arranca sobre lo que ya tiene.
set -eu

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  mkdir -p "$PGDATA"
  chown postgres:postgres "$PGDATA"
  chmod 0700 "$PGDATA"
  until pg_isready -h primario -U "$POSTGRES_USER" -q; do sleep 1; done
  su-exec postgres pg_basebackup \
    -d "host=primario user=$POSTGRES_USER password=$POSTGRES_PASSWORD application_name=replica" \
    -D "$PGDATA" -X stream -R -C -S replica
fi

exec su-exec postgres postgres -c hba_file=/etc/postgresql/pg_hba.conf
