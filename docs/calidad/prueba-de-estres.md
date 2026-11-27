# Prueba de estrés hasta el punto de quiebre · HU-14, escenario 2

**AYNI-300** · ejecutada el 26-11-2026 · base para documentar la degradación (AYNI-301) y
optimizar los cuellos de botella (AYNI-302).

## Resultado

**El punto de quiebre está en 800 usuarios concurrentes**: es el primer escalón en que la
latencia P95 supera 1 s (1 107 ms). Hasta 400 usuarios se cumple el objetivo de HU-14
(P95 < 500 ms) y con 200, la carga nominal de la historia, el P95 es de 106 ms. Pasado el
quiebre el sistema **degrada de forma controlada**: la latencia crece, pero ningún servicio se
reinicia ni se queda sin memoria, no hay errores en los escalones de 600 a 1 000 usuarios y los
saldos de las 50 cuentas no cambian.

| Usuarios concurrentes | P95 | P99 | Máximo | Errores | Estado |
|---|---|---|---|---|---|
| 50 | 5 ms | 44 ms | 213 ms | 0.00 % | dentro del objetivo |
| 100 | 5 ms | 85 ms | 483 ms | 0.00 % | dentro del objetivo |
| 200 | 106 ms | 201 ms | 984 ms | 0.00 % | dentro del objetivo |
| 300 | 296 ms | 394 ms | 1 651 ms | 0.00 % | dentro del objetivo |
| 400 | 398 ms | 502 ms | 2 680 ms | 0.08 % | dentro del objetivo |
| 500 | 605 ms | 803 ms | 7 171 ms | 0.26 % | sobre 500 ms |
| 600 | 732 ms | 998 ms | 3 448 ms | 0.00 % | sobre 500 ms |
| 800 | 1 107 ms | 1 395 ms | 2 986 ms | 0.00 % | **punto de quiebre** |
| 1 000 | 1 414 ms | 1 796 ms | 3 535 ms | 0.00 % | degradado |

Latencias solo de respuestas correctas: un rechazo es instantáneo y, mezclado, haría bajar el
P95 justo cuando el sistema falla. Los fallos van en su columna.

- **756 072 peticiones** en 18 minutos, 698 por segundo de media.
- **348 respuestas distintas de 200 (0.05 %)**, todas en los escalones de 400 y 500. Ni el
  gateway ni core-banking registraron errores en ese tramo, así que no salieron de la
  aplicación; quedan por explicar en AYNI-301. Con 500 usuarios superan el 0.1 % que HU-14
  admite con carga nominal.

## Cuello de botella

Uso de CPU durante la prueba, en porcentaje de un núcleo, frente al límite de cada contenedor:

| Contenedor | Límite | CPU p50 | CPU p95 | Máximo | Memoria máxima / límite |
|---|---|---|---|---|---|
| ayni-gateway | 0.50 | 49 % | 52 % | 54 % | 344 MiB / 640 MiB |
| ayni-core-banking-service | 0.75 | 66 % | 76 % | 80 % | 487 MiB / 768 MiB |
| ayni-postgres | 1.00 | 41 % | 48 % | 60 % | 100 MiB / 512 MiB |
| ayni-redis | 0.25 | 0 % | 3 % | 5 % | 10 MiB / 64 MiB |

**Gateway y core-banking trabajan contra su techo de CPU**; la base de datos y Redis van
holgados y la memoria sobra en todos. La latencia crece porque las peticiones esperan CPU en
esos dos servicios, no por la base de datos. Es el punto de partida de AYNI-302: más CPU o
réplicas de esos dos (AYNI-306) mueven el quiebre; optimizar consultas, de momento, no.

## Integridad durante la degradación

HU-14 pide que el sistema degrade «sin corromper datos ni perder transacciones». Al terminar se
consultaron las 50 cuentas: las 50 conservan su saldo exacto de S/ 450.00 (los tres depósitos
de preparación). Ningún contenedor se reinició ni fue detenido por falta de memoria
(`RestartCount=0`, `OOMKilled=false`). La prueba solo lee: la integridad de las escrituras bajo
carga se prueba aparte con las transferencias concurrentes de core-banking.

## Entorno y método

| | |
|---|---|
| Sistema | `develop` en `d24726d`, compose local (`infra/docker/docker-compose.yml`) |
| Servicios | gateway, core-banking, PostgreSQL 16, RabbitMQ y Redis. Identity no interviene: los tokens se emiten con la clave de desarrollo |
| Límites | los de producción (`docker-compose.prod.yml`): gateway 0.5 CPU / 640 MB, core-banking 0.75 / 768 MB, Redis 0.25 / 64 MB, RabbitMQ 0.5 / 384 MB; PostgreSQL con el del compose local (1 CPU / 512 MB) |
| Máquina | Windows 11, Docker Desktop con 12 CPU y 7.6 GB |
| Carga | k6 1.4.2 en contenedor, `infra/carga/estres.js`, escalones de 2 minutos |
| Datos | 50 clientes con cuenta, tarjeta y 3 depósitos, creados con `infra/carga/preparar-clientes.py` |
| Consultas | por usuario virtual y segundo: cuenta, movimientos, rendimiento y tarjeta |

Se hicieron dos ejecuciones. La primera (25 a 400 usuarios) no llegó al quiebre: con 400 el P95
fue de 496 ms. La segunda, la de la tabla, repitió desde 50 hasta 1 000 para tener todos los
escalones en una sola corrida.

### Qué tan representativo es de producción

- Cada contenedor tuvo **su límite completo**, porque la máquina tenía 12 CPU libres. La
  instancia de producción (`m7i-flex.large`) tiene **2 vCPU** y la suma de los límites de sus
  contenedores es mayor, así que allí compiten entre sí: **el quiebre en producción llegará
  antes de 800 usuarios**. Repetir en staging con su instancia real da la cifra definitiva.
- La base de producción es RDS, no un contenedor; aquí no fue el cuello de botella.
- k6 y el sistema compartieron máquina; con 1 000 usuarios virtuales k6 consume CPU propia.

## Cómo repetirla

```bash
cp .env.example .env    # y generar los secretos
C="docker compose --env-file .env -f infra/docker/docker-compose.yml"
$C up -d --wait postgres rabbitmq redis
$C up -d --build --wait --no-deps ayni-core-banking-service ayni-gateway
# Límites de producción
docker update --cpus 0.5  --memory 640m --memory-swap 640m ayni-gateway
docker update --cpus 0.75 --memory 768m --memory-swap 768m ayni-core-banking-service
docker update --cpus 0.25 --memory 64m  --memory-swap 64m  ayni-redis
docker update --cpus 0.5  --memory 384m --memory-swap 384m ayni-rabbitmq

python infra/carga/preparar-clientes.py --clientes 50 > tokens.txt
docker run --rm -v "$PWD:/w" -w /w grafana/k6:1.4.2 run \
  -e BASE_URL=http://host.docker.internal:8080 -e TOKENS="$(cat tokens.txt)" \
  -e NIVELES=50,100,200,300,400,500,600,800,1000 -e DURACION=2m infra/carga/estres.js
```

`--no-deps` evita arrancar MinIO, que esta prueba no usa: `minio/mc` ya no se puede descargar
de Docker Hub y hoy impide el `docker compose up` completo.
