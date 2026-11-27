# Disponibilidad del mes contrastada con el 99.5 % · HU-16

**AYNI-312** · medición sobre un **mes simulado** · 26-11-2026

## Por qué simulado

HU-16 compromete un 99.5 % de disponibilidad **mensual medida con Prometheus**. Producción no
tiene ese mes: Prometheus guardaba 15 días, y la retención de 35 días (PR #117) está en
`develop` pero aún no se ha desplegado, porque `main` sigue en la versión 2.0.0. Los datos
solo se acumulan desde ese despliegue.

Para no llegar al cierre sin medición, se simuló un mes completo con incidentes que se apoyan
en mecanismos reales del sistema, se cargó en un Prometheus real y se midió con **el mismo
script que se usará con los datos de producción**. Lo que se demuestra es el método y que la
cifra sale bien calculada; la disponibilidad real se mide igual en cuanto haya un mes de datos.

## Resultado

| Indicador | Valor |
|---|---|
| Ventana | 28-10-2026 02:02 → 27-11-2026 02:02 UTC (30 días) |
| Disponibilidad del banco (gateway + identity + core-banking a la vez) | **99.912 %** |
| Objetivo de HU-16 | 99.5 % |
| Caída acumulada | 38.0 min |
| Presupuesto de caída del objetivo | 216.0 min |
| Resultado | **CUMPLE**, con 178 minutos de margen |

| Servicio | Esencial | Disponibilidad | Caída (min) |
|---|---|---|---|
| ayni-gateway | sí | 99.919 % | 35.0 |
| ayni-identity-service | sí | 99.919 % | 35.0 |
| ayni-core-banking-service | sí | 99.912 % | 38.0 |
| ayni-notification-service | no | 99.896 % | 45.0 |
| ayni-kyc-service | no | 99.972 % | 12.0 |

notification y kyc no restan a la cifra del banco: según HU-16, su caída no impide operar con
el dinero. Se informan aparte.

## Incidentes del mes simulado

Definidos en `infra/observability/simulacion/incidentes-de-un-mes.json`.

| Día | Duración | Afecta a | Qué lo produce |
|---|---|---|---|
| 3, 10, 17 y 24 | 75 s cada uno | gateway, identity, core-banking, notification | Despliegue semanal: `desplegar.sh` recrea los contenedores y espera su health check |
| 8 | 3 min | core-banking | Supera su límite de 768 MB y Docker lo reinicia (`restart: unless-stopped`) |
| 12 | 10 min | notification | Prueba de caída durante una transferencia (AYNI-310) |
| 12 | 10 min | kyc | Prueba de caída durante un onboarding (AYNI-311) |
| 19 | 28 min | gateway, identity, core-banking, notification | Simulacro de restauración de la base de datos (AYNI-308) |
| 27 | 2 min | todos, **sin muestras** | Prometheus se reinicia al actualizarse: nadie sabe si el banco estuvo arriba |

## Cómo se comprobó que la cifra es correcta

1. El generador calcula a partir de la lista de incidentes la caída que **debería** salir:
   38.0 min para el banco y la de cada servicio.
2. Los 30 días de `up` (864 000 muestras) se cargaron con
   `promtool tsdb create-blocks-from openmetrics` en un Prometheus 3.4.0, la misma versión
   que producción.
3. `medir-disponibilidad.py` midió **exactamente** esas cifras, servicio por servicio.

La simulación encontró un defecto del script, ya corregido. La versión anterior evaluaba el
banco con una consulta instantánea, que mira hasta 5 minutos atrás y rellenaba con el último
valor el hueco en que Prometheus estuvo caído: daba 36.0 min y 99.917 %, sin contar los 2
minutos sin muestras. Ahora cada paso lee solo la ventana de un raspado, y un paso sin muestra
cuenta como caída.

## Cómo repetirlo

```bash
python infra/scripts/simular-mes-de-disponibilidad.py \
    infra/observability/simulacion/incidentes-de-un-mes.json > mes.om
# escribe en pantalla la caída esperada y el instante final (--hasta)

mkdir datos
docker run --rm --entrypoint promtool -v "$PWD:/sim" prom/prometheus:v3.4.0 \
    tsdb create-blocks-from openmetrics /sim/mes.om /sim/datos
printf 'global:\n  scrape_interval: 15s\nscrape_configs: []\n' > prometheus.yml
docker run -d --name prom-mes --user root -p 9092:9090 \
    -v "$PWD/prometheus.yml:/etc/prometheus/prometheus.yml:ro" -v "$PWD/datos:/prometheus" \
    prom/prometheus:v3.4.0 --config.file=/etc/prometheus/prometheus.yml \
    --storage.tsdb.path=/prometheus --storage.tsdb.retention.time=35d

python infra/scripts/medir-disponibilidad.py --prometheus http://localhost:9092 --hasta <fin>
```

## Con los datos reales

En cuanto la retención de 35 días esté desplegada y pasen 30 días, en la instancia:

```bash
python3 medir-disponibilidad.py --contenedor ayni-prometheus > disponibilidad.md
```

`--hasta` permite medir un mes cerrado (por ejemplo `--hasta 2026-12-01T05:00:00Z` para
noviembre en hora de Lima). Si la retención no cubre la ventana, el informe lo avisa y la
cifra pasa a ser un límite inferior.
