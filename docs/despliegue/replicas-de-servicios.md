# Réplicas de los servicios sin estado

**Historia:** HU-16 · AYNI-306 (sobre el laboratorio de escalado de AYNI-319)

## Qué se replica y por qué

| Servicio | Réplicas | Por qué es seguro (o no) |
|---|---|---|
| Gateway | 2 | La sesión viaja en el token y el límite de peticiones vive en Redis (ADR-0035), compartido por las dos. |
| identity | 2 | Sesiones, desafíos, códigos usados y roles viven en PostgreSQL. No tiene tareas programadas. El republicado de titulares (`AYNI_REPUBLICAR_TITULARES`), que solo corre al arrancar, queda apagado en la copia. |
| kyc | 2 | Cada verificación es una petición independiente; los documentos están en MinIO. |
| core-banking | **1** | Tiene tareas programadas: cierre diario y devengo, conciliación interbancaria, publicación del outbox y mantenimiento de tablas. Con dos copias correrían dos veces. |
| notification | **1** | El despacho programado de avisos correría en las dos copias. |
| PostgreSQL | 1 (+ réplica HA en AWS) | Escala en vertical con su réplica de alta disponibilidad (ADR-0037). |

Para replicar core-banking o notification antes hay que poner un candado distribuido en sus tareas
programadas (por ejemplo, un *advisory lock* de PostgreSQL o ShedLock), de modo que solo una copia ejecute
cada tarea. Los escuchadores de RabbitMQ ya admitirían varias copias (consumidores en competencia, con
deduplicación por id de evento en notification).

## Cómo está montado

```
cliente ─▶ nginx :8088 ─▶ gateway ×2 ─▶ nginx :8081 ─▶ identity ×2 ─▶ nginx :8000 ─▶ kyc ×2
                                    └─▶ core-banking ×1
```

- Un solo nginx (`infra/docker/escalado/nginx.conf`) con un `upstream` por servicio, reparto por turnos y
  `max_fails=1 fail_timeout=10s`: una instancia que falla se salta durante 10 s.
- Los servicios se encuentran por sus variables de siempre, apuntadas al balanceador:
  `AYNI_IDENTITY_URI` en el gateway y `AYNI_KYC_SERVICE_URL` en identity. No cambia nada de código ni de
  producción.
- Solo se reintenta en la otra instancia lo que no llegó a procesarse (error de conexión o tiempo de
  espera). nginx no repite un POST ya enviado, así que un inicio de sesión o un registro no se ejecutan dos
  veces.
- Cada respuesta dice qué instancia atendió (`X-Ayni-Instancia` y `X-Ayni-Instancia-Identity`).

## Cómo se levanta

```bash
docker compose --env-file .env -f infra/docker/docker-compose.yml \
               -f infra/docker/escalado/docker-compose.yml up -d --wait
```

## Evidencia (26-nov-2026, entorno local)

| Prueba | Resultado |
|---|---|
| 10 inicios de sesión por `:8088` | Alternan entre los dos gateways y entre las dos instancias de identity. Desde el sexto, 429: el límite de 5 por minuto se cuenta una sola vez aunque haya dos gateways (Redis compartido). |
| 10 consultas con las dos instancias de identity | 10/10 respuestas 200, repartidas entre las dos. |
| 10 consultas con `identity-2` detenida | **10/10 respuestas 200.** nginx intentó la instancia caída, la marcó y reintentó en la otra (la cabecera muestra las dos direcciones). |

El script de la prueba de caída está en `herramientas/resiliencia/caida-de-una-replica.ps1` (fuera del
repositorio, con las credenciales de prueba locales).
